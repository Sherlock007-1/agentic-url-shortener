package com.agenticsdlc.orchestrator.engine;

import com.agenticsdlc.orchestrator.agent.Agent;
import com.agenticsdlc.orchestrator.agent.AgentContext;
import com.agenticsdlc.orchestrator.agent.AgentDecision;
import com.agenticsdlc.orchestrator.agent.AgentFallback;
import com.agenticsdlc.orchestrator.agent.AgentRegistry;
import com.agenticsdlc.orchestrator.agent.AgentResult;
import com.agenticsdlc.orchestrator.agent.FallbackRegistry;
import com.agenticsdlc.orchestrator.config.OrchestratorConfiguration;
import com.agenticsdlc.orchestrator.domain.AttemptKind;
import com.agenticsdlc.orchestrator.domain.TaskAttempt;
import com.agenticsdlc.orchestrator.engine.WorkflowTransitionService.ClaimedTask;
import com.agenticsdlc.orchestrator.governance.AgentTimeoutException;
import com.agenticsdlc.orchestrator.governance.BudgetGuard;
import com.agenticsdlc.orchestrator.governance.FailureClassifier;
import com.agenticsdlc.orchestrator.governance.RecoveryService;
import com.agenticsdlc.orchestrator.governance.SafeStopService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Runs a single claimed task under the controlled-autonomy rules.
 *
 * <p>The agent itself is still invoked outside any transaction; what changed with
 * the governance increment is what happens around it:
 *
 * <ol>
 * <li>every attempt is bounded by {@code orchestrator.governance.task-timeout},</li>
 * <li>a failure is classified (only {@code RetryableAgentException} and timeouts
 * are retryable) and retried at most {@code max-task-attempts} times,</li>
 * <li>when the primary agent is exhausted an <em>approved</em> fallback may run
 * once, producing an explicitly degraded result,</li>
 * <li>if there is no fallback, or it fails, the workflow is safe-stopped.</li>
 * </ol>
 *
 * <p>None of this is the engine's optimistic-locking retry, which stays in
 * {@link WorkflowEngine} and is not an agent retry.
 */
@Component
public class TaskRunner {

	private static final Logger log = LoggerFactory.getLogger(TaskRunner.class);

	private final AgentRegistry agentRegistry;
	private final FallbackRegistry fallbackRegistry;
	private final WorkflowTransitionService transitionService;
	private final RecoveryService recoveryService;
	private final SafeStopService safeStopService;
	private final BudgetGuard budgetGuard;
	private final FailureClassifier failureClassifier;
	private final ExecutorService attemptExecutor;

	public TaskRunner(AgentRegistry agentRegistry, FallbackRegistry fallbackRegistry,
			WorkflowTransitionService transitionService, RecoveryService recoveryService,
			SafeStopService safeStopService, BudgetGuard budgetGuard, FailureClassifier failureClassifier,
			@Qualifier(OrchestratorConfiguration.AGENT_TIMEOUT_EXECUTOR) ExecutorService attemptExecutor) {
		this.agentRegistry = agentRegistry;
		this.fallbackRegistry = fallbackRegistry;
		this.transitionService = transitionService;
		this.recoveryService = recoveryService;
		this.safeStopService = safeStopService;
		this.budgetGuard = budgetGuard;
		this.failureClassifier = failureClassifier;
		this.attemptExecutor = attemptExecutor;
	}

	public void run(UUID workflowRunId, ClaimedTask claimed) {
		AgentContext context = new AgentContext(workflowRunId, claimed.taskId(), claimed.taskKey(),
				claimed.requirement(), claimed.upstreamOutputs());
		int maxAttempts = budgetGuard.maxTaskAttempts();
		String lastError = null;

		for (int attemptNo = 1; attemptNo <= maxAttempts; attemptNo++) {
			TaskAttempt attempt = recoveryService.beginAttempt(workflowRunId, claimed.taskId(), AttemptKind.PRIMARY);
			try {
				Agent agent = agentRegistry.require(claimed.agentType());
				AgentResult result = invoke(() -> agent.execute(context), claimed.taskKey());
				recoveryService.recordAttemptSucceeded(attempt.getId());
				if (attemptNo > 1) {
					recoveryService.recordRecovered(workflowRunId, claimed.taskId(), attemptNo, AttemptKind.PRIMARY);
				}
				transitionService.completeTask(claimed.taskId(), result.output(), result.summary(), result.decisions(),
						claimed.upstreamOutputs());
				return;
			}
			catch (RuntimeException ex) {
				boolean retryable = failureClassifier.isRetryable(ex);
				lastError = failureClassifier.describe(ex);
				log.warn("Task {} ({}) attempt {}/{} failed (retryable={}): {}", claimed.taskKey(), claimed.taskId(),
						attemptNo, maxAttempts, retryable, lastError);
				recoveryService.recordAttemptFailed(attempt.getId(), lastError, retryable);

				if (!retryable) {
					// Permanent defect: fail fast, never loop, never fall back.
					transitionService.failTask(claimed.taskId(), lastError, claimed.upstreamOutputs());
					return;
				}
				if (attemptNo < maxAttempts) {
					recoveryService.scheduleRetry(claimed.taskId(), attemptNo + 1, maxAttempts, lastError);
				}
			}
		}

		exhausted(workflowRunId, claimed, context, maxAttempts, lastError);
	}

	/** Bounded retries did not help: try one approved fallback, else safe-stop. */
	private void exhausted(UUID workflowRunId, ClaimedTask claimed, AgentContext context, int maxAttempts,
			String lastError) {
		Optional<AgentFallback> fallback = fallbackRegistry.find(claimed.agentType());
		if (fallback.isEmpty()) {
			String reason = "Task '" + claimed.taskKey() + "' exhausted " + maxAttempts
					+ " bounded attempts and no approved fallback is registered for agent " + claimed.agentType()
					+ ". Last error: " + lastError;
			safeStopService.safeStop(workflowRunId, claimed.taskId(), reason);
			return;
		}

		AgentFallback approved = fallback.get();
		recoveryService.recordFallbackInvoked(workflowRunId, claimed.taskId(), approved.description());
		TaskAttempt attempt = recoveryService.beginAttempt(workflowRunId, claimed.taskId(), AttemptKind.FALLBACK);
		try {
			AgentResult result = invoke(() -> approved.execute(context), claimed.taskKey());
			recoveryService.recordAttemptSucceeded(attempt.getId());
			recoveryService.recordFallbackSucceeded(workflowRunId, claimed.taskId(), approved.description());
			recoveryService.recordRecovered(workflowRunId, claimed.taskId(), attempt.getAttemptNo(),
					AttemptKind.FALLBACK);
			transitionService.completeTask(claimed.taskId(), degradedOutput(result.output()),
					"DEGRADED (fallback): " + result.summary(), degradedDecisions(result, approved, lastError),
					claimed.upstreamOutputs());
		}
		catch (RuntimeException ex) {
			String error = failureClassifier.describe(ex);
			recoveryService.recordAttemptFailed(attempt.getId(), error, false);
			recoveryService.recordFallbackFailed(workflowRunId, claimed.taskId(), error);
			safeStopService.safeStop(workflowRunId, claimed.taskId(),
					"Task '" + claimed.taskKey() + "' exhausted " + maxAttempts
							+ " bounded attempts and the approved fallback also failed: " + error);
		}
	}

	private String degradedOutput(String output) {
		return "[FALLBACK/DEGRADED] " + output;
	}

	/** Makes the degradation part of the persisted decision lineage. */
	private List<AgentDecision> degradedDecisions(AgentResult result, AgentFallback fallback, String lastError) {
		List<AgentDecision> decisions = new ArrayList<>(result.decisions());
		decisions.add(new AgentDecision("FALLBACK", "Degraded result accepted from approved fallback",
				"The primary agent exhausted its bounded retries (last error: " + lastError
						+ "). The approved fallback (" + fallback.description()
						+ ") produced a reduced-confidence result; it is not equivalent to a successful primary "
						+ "agent execution."));
		return decisions;
	}

	/**
	 * Executes one attempt with a hard time budget on a dedicated thread, so a hung
	 * agent cannot block the orchestration worker pool indefinitely.
	 */
	private AgentResult invoke(java.util.function.Supplier<AgentResult> call, String taskKey) {
		Future<AgentResult> future = attemptExecutor.submit(call::get);
		try {
			return future.get(budgetGuard.taskTimeout().toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException ex) {
			future.cancel(true);
			throw new AgentTimeoutException(taskKey, budgetGuard.taskTimeout());
		}
		catch (ExecutionException ex) {
			Throwable cause = ex.getCause();
			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			throw new IllegalStateException(cause == null ? ex.getMessage() : cause.getMessage(), cause);
		}
		catch (InterruptedException ex) {
			future.cancel(true);
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while executing task '" + taskKey + "'", ex);
		}
	}
}