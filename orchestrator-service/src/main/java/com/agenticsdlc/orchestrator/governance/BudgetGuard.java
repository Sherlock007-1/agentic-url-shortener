package com.agenticsdlc.orchestrator.governance;

import com.agenticsdlc.orchestrator.config.GovernanceProperties;
import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.engine.AuditService;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autonomy boundaries of a run.
 *
 * <p>Three practical limits, all configured under
 * {@code orchestrator.governance}: attempts per task, wall-clock duration per
 * workflow and execution time per agent attempt. Token accounting is deliberately
 * out of scope for the deterministic agents used here.
 *
 * <p>Exceeding a boundary never leads to "try harder": the caller safe-stops.
 */
@Component
public class BudgetGuard {

	private final GovernanceProperties governance;
	private final AuditService auditService;
	private final Clock clock;

	public BudgetGuard(GovernanceProperties governance, AuditService auditService, Clock clock) {
		this.governance = governance;
		this.auditService = auditService;
		this.clock = clock;
	}

	public int maxTaskAttempts() {
		return governance.maxTaskAttempts();
	}

	public Duration taskTimeout() {
		return governance.taskTimeout();
	}

	public Duration maxWorkflowDuration() {
		return governance.maxWorkflowDuration();
	}

	/**
	 * @return the reason when the wall-clock budget of the run is exhausted
	 */
	public Optional<String> workflowBudgetExceeded(WorkflowRun run) {
		if (run.getStartedAt() == null) {
			return Optional.empty();
		}
		Duration elapsed = Duration.between(run.getStartedAt(), clock.instant());
		if (elapsed.compareTo(governance.maxWorkflowDuration()) <= 0) {
			return Optional.empty();
		}
		return Optional.of("Workflow wall-clock budget exceeded: elapsed " + elapsed + " > allowed "
				+ governance.maxWorkflowDuration());
	}

	/** Audits the boundary breach; the caller performs the safe stop. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordBudgetExceeded(UUID workflowRunId, UUID taskId, String reason) {
		auditService.record(workflowRunId, taskId, AuditEventType.BUDGET_EXCEEDED, "Autonomy budget exceeded", reason);
	}
}
