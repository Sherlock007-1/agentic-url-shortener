package com.agenticsdlc.orchestrator.governance;

import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.domain.TaskStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowTask;
import com.agenticsdlc.orchestrator.engine.AuditService;
import com.agenticsdlc.orchestrator.engine.WorkflowNotFoundException;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowTaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place that stops a workflow in a controlled way.
 *
 * <p>A safe stop is what happens when an autonomy boundary is reached: exhausted
 * retries without an approved fallback, a rejected approval, a policy rejection or
 * an exceeded budget. It is terminal and always leaves a persisted reason plus a
 * {@code WORKFLOW_SAFE_STOPPED} audit event, so the run never keeps burning
 * resources and can always be explained afterwards.
 */
@Service
public class SafeStopService {

	private final WorkflowRunRepository workflowRunRepository;
	private final WorkflowTaskRepository taskRepository;
	private final AuditService auditService;
	private final Clock clock;

	public SafeStopService(WorkflowRunRepository workflowRunRepository, WorkflowTaskRepository taskRepository,
			AuditService auditService, Clock clock) {
		this.workflowRunRepository = workflowRunRepository;
		this.taskRepository = taskRepository;
		this.auditService = auditService;
		this.clock = clock;
	}

	/**
	 * Stops the workflow with an auditable reason and optionally fails the task that
	 * triggered the boundary.
	 *
	 * @return true when this call performed the stop
	 */
	@Transactional
	public boolean safeStop(UUID workflowRunId, UUID taskId, String reason) {
		WorkflowRun run = workflowRunRepository.findById(workflowRunId)
				.orElseThrow(() -> new WorkflowNotFoundException("Workflow " + workflowRunId + " not found"));
		if (run.getStatus().isTerminal()) {
			return false;
		}
		Instant now = clock.instant();
		if (taskId != null) {
			taskRepository.findById(taskId).ifPresent(task -> failIfNotTerminal(task, reason, now));
		}
		run.markSafeStopped(reason, now);
		workflowRunRepository.save(run);
		auditService.record(workflowRunId, taskId, AuditEventType.WORKFLOW_SAFE_STOPPED,
				"Workflow safe-stopped", reason);
		return true;
	}

	private void failIfNotTerminal(WorkflowTask task, String reason, Instant now) {
		if (task.getStatus() != TaskStatus.COMPLETED) {
			task.markFailed(reason, now);
			taskRepository.save(task);
		}
	}

	/**
	 * Records a governance event and safe-stops in one committed transaction.
	 *
	 * <p>Used by callers that report the boundary breach by throwing: the evidence
	 * must survive the exception, so it must not share the caller's transaction.
	 */
	@Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
	public void recordAndSafeStop(UUID workflowRunId, UUID taskId,
			com.agenticsdlc.orchestrator.domain.AuditEventType eventType, String message, String details,
			String reason) {
		auditService.record(workflowRunId, taskId, eventType, message, details);
		safeStopInCurrentTransaction(workflowRunId, taskId, reason);
	}

	private void safeStopInCurrentTransaction(UUID workflowRunId, UUID taskId, String reason) {
		WorkflowRun run = workflowRunRepository.findById(workflowRunId)
				.orElseThrow(() -> new WorkflowNotFoundException("Workflow " + workflowRunId + " not found"));
		if (run.getStatus().isTerminal()) {
			return;
		}
		Instant now = clock.instant();
		if (taskId != null) {
			taskRepository.findById(taskId).ifPresent(task -> failIfNotTerminal(task, reason, now));
		}
		run.markSafeStopped(reason, now);
		workflowRunRepository.save(run);
		auditService.record(workflowRunId, taskId, AuditEventType.WORKFLOW_SAFE_STOPPED, "Workflow safe-stopped",
				reason);
	}
}