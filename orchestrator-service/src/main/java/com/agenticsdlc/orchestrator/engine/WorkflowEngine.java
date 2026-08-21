package com.agenticsdlc.orchestrator.engine;

import com.agenticsdlc.orchestrator.config.OrchestratorConfiguration;
import com.agenticsdlc.orchestrator.engine.WorkflowTransitionService.ClaimedTask;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Single-instance orchestration engine.
 *
 * <p>{@link #advance(UUID)} claims all currently eligible tasks and dispatches them
 * to the bounded worker pool, so independent branches of the graph execute in
 * parallel while dependent tasks wait for their predecessors.
 *
 * <p>Because the assessment targets exactly one orchestrator instance, mutual
 * exclusion is a per-workflow JVM lock rather than distributed row locking;
 * entity {@code @Version} columns still guard against lost updates.
 */
@Component
public class WorkflowEngine {

	private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

	private final WorkflowTransitionService transitionService;
	private final TaskRunner taskRunner;
	private final TaskExecutor taskExecutor;
	private final Map<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

	public WorkflowEngine(WorkflowTransitionService transitionService, TaskRunner taskRunner,
			@Qualifier(OrchestratorConfiguration.TASK_EXECUTOR) TaskExecutor taskExecutor) {
		this.transitionService = transitionService;
		this.taskRunner = taskRunner;
		this.taskExecutor = taskExecutor;
	}

	/**
	 * Moves a workflow as far forward as its persisted state allows: eligible tasks
	 * are claimed and submitted, and the workflow is finalised when everything is
	 * terminal.
	 */
	public void advance(UUID workflowRunId) {
		List<ClaimedTask> claimed;
		// The lock instance must stay stable for the lifetime of the run, otherwise
		// two threads could serialise on different objects and claim the same task.
		ReentrantLock lock = locks.computeIfAbsent(workflowRunId, key -> new ReentrantLock());
		lock.lock();
		try {
			// Claiming under the lock guarantees a task is dispatched exactly once.
			claimed = transitionService.claimEligibleTasks(workflowRunId);
			if (claimed.isEmpty()) {
				transitionService.finalizeIfFinished(workflowRunId);
			}
		}
		catch (OptimisticLockingFailureException ex) {
			// Another thread progressed the same rows; the next completion or poll retries.
			log.debug("Concurrent update while advancing workflow {}, will retry", workflowRunId);
			return;
		}
		finally {
			lock.unlock();
		}

		for (ClaimedTask task : claimed) {
			taskExecutor.execute(() -> executeAndContinue(workflowRunId, task));
		}
	}

	private void executeAndContinue(UUID workflowRunId, ClaimedTask task) {
		try {
			taskRunner.run(workflowRunId, task);
		}
		finally {
			// Completing a task can unblock successors, so drive the workflow again.
			try {
				advance(workflowRunId);
			}
			catch (RuntimeException ex) {
				log.error("Failed to advance workflow {}", workflowRunId, ex);
			}
		}
	}
}
