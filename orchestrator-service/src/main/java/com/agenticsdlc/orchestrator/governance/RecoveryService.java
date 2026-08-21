package com.agenticsdlc.orchestrator.governance;

import com.agenticsdlc.orchestrator.domain.AttemptKind;
import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.domain.TaskAttempt;
import com.agenticsdlc.orchestrator.domain.WorkflowTask;
import com.agenticsdlc.orchestrator.engine.AuditService;
import com.agenticsdlc.orchestrator.engine.WorkflowNotFoundException;
import com.agenticsdlc.orchestrator.repository.TaskAttemptRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowTaskRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the recovery ledger of a task: bounded agent attempts, retries,
 * fallback invocations and recoveries.
 *
 * <p>Everything recorded here is an <em>agent/task</em> attempt. The engine's
 * optimistic-locking retry (a concurrency mechanism, see {@code WorkflowEngine})
 * is a different concept and deliberately never appears in this ledger, in the
 * audit trail as a retry, or in the retry metrics.
 */
@Service
public class RecoveryService {

	private final TaskAttemptRepository attemptRepository;
	private final WorkflowTaskRepository taskRepository;
	private final AuditService auditService;
	private final Clock clock;

	public RecoveryService(TaskAttemptRepository attemptRepository, WorkflowTaskRepository taskRepository,
			AuditService auditService, Clock clock) {
		this.attemptRepository = attemptRepository;
		this.taskRepository = taskRepository;
		this.auditService = auditService;
		this.clock = clock;
	}

	/** Opens an attempt row and bumps the persisted attempt counter of the task. */
	@Transactional
	public TaskAttempt beginAttempt(UUID workflowRunId, UUID taskId, AttemptKind kind) {
		WorkflowTask task = requireTask(taskId);
		int attemptNo = task.startAttempt();
		taskRepository.save(task);
		return attemptRepository.save(new TaskAttempt(workflowRunId, taskId, attemptNo, kind, clock.instant()));
	}

	@Transactional
	public void recordAttemptSucceeded(UUID attemptId) {
		TaskAttempt attempt = requireAttempt(attemptId);
		attempt.succeed(clock.instant());
		attemptRepository.save(attempt);
	}

	@Transactional
	public void recordAttemptFailed(UUID attemptId, String error, boolean retryable) {
		TaskAttempt attempt = requireAttempt(attemptId);
		attempt.fail(error, retryable, clock.instant());
		attemptRepository.save(attempt);
		auditService.record(attempt.getWorkflowRunId(), attempt.getTaskId(), AuditEventType.TASK_ATTEMPT_FAILED,
				"Agent attempt " + attempt.getAttemptNo() + " (" + attempt.getKind() + ") failed, retryable="
						+ retryable,
				error);
	}

	/** Marks the task RETRYING and audits that another bounded attempt follows. */
	@Transactional
	public void scheduleRetry(UUID taskId, int nextAttemptNo, int maxAttempts, String error) {
		WorkflowTask task = requireTask(taskId);
		task.markRetrying(error);
		taskRepository.save(task);
		auditService.record(task.getWorkflowRunId(), taskId, AuditEventType.TASK_RETRY_SCHEDULED,
				"Retry " + (nextAttemptNo - 1) + " of " + (maxAttempts - 1) + " scheduled for task '"
						+ task.getTaskKey() + "' (attempt " + nextAttemptNo + "/" + maxAttempts + ")",
				error);
	}

	/** A later attempt succeeded after an earlier one failed. */
	@Transactional
	public void recordRecovered(UUID workflowRunId, UUID taskId, int attemptNo, AttemptKind kind) {
		WorkflowTask task = requireTask(taskId);
		auditService.record(workflowRunId, taskId, AuditEventType.TASK_RECOVERED,
				"Task '" + task.getTaskKey() + "' recovered on attempt " + attemptNo + " (" + kind + ")");
	}

	@Transactional
	public void recordFallbackInvoked(UUID workflowRunId, UUID taskId, String description) {
		auditService.record(workflowRunId, taskId, AuditEventType.FALLBACK_INVOKED,
				"Approved fallback invoked after retries were exhausted", description);
	}

	@Transactional
	public void recordFallbackSucceeded(UUID workflowRunId, UUID taskId, String description) {
		auditService.record(workflowRunId, taskId, AuditEventType.FALLBACK_SUCCEEDED,
				"Fallback produced a degraded result (not equivalent to a successful primary agent run)", description);
	}

	@Transactional
	public void recordFallbackFailed(UUID workflowRunId, UUID taskId, String error) {
		auditService.record(workflowRunId, taskId, AuditEventType.FALLBACK_FAILED, "Fallback failed", error);
	}

	@Transactional(readOnly = true)
	public List<TaskAttempt> attempts(UUID taskId) {
		return attemptRepository.findByTaskIdOrderByAttemptNoAsc(taskId);
	}

	private TaskAttempt requireAttempt(UUID attemptId) {
		return attemptRepository.findById(attemptId)
				.orElseThrow(() -> new WorkflowNotFoundException("Task attempt " + attemptId + " not found"));
	}

	private WorkflowTask requireTask(UUID taskId) {
		return taskRepository.findById(taskId)
				.orElseThrow(() -> new WorkflowNotFoundException("Task " + taskId + " not found"));
	}
}
