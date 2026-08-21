package com.agenticsdlc.orchestrator.governance;

import com.agenticsdlc.orchestrator.config.GovernanceProperties;
import com.agenticsdlc.orchestrator.domain.Approval;
import com.agenticsdlc.orchestrator.domain.ApprovalGate;
import com.agenticsdlc.orchestrator.domain.ApprovalStatus;
import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.domain.Decision;
import com.agenticsdlc.orchestrator.domain.TaskStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowTask;
import com.agenticsdlc.orchestrator.engine.AuditService;
import com.agenticsdlc.orchestrator.engine.IllegalWorkflowStateException;
import com.agenticsdlc.orchestrator.engine.WorkflowNotFoundException;
import com.agenticsdlc.orchestrator.repository.ApprovalRepository;
import com.agenticsdlc.orchestrator.repository.DecisionRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowTaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Human approval gates.
 *
 * <p>The orchestrator never approves anything itself: {@link #requireGate} only
 * <em>requests</em> an approval and reports that execution is blocked. A human
 * (Swagger in this assessment) resolves it through {@link #approve} or
 * {@link #reject}. A rejection is not a failure and not a silent continue - it is
 * an audited controlled stop.
 */
@Service
public class ApprovalService {

	private final ApprovalRepository approvalRepository;
	private final WorkflowRunRepository workflowRunRepository;
	private final WorkflowTaskRepository taskRepository;
	private final DecisionRepository decisionRepository;
	private final AuditService auditService;
	private final SafeStopService safeStopService;
	private final GovernanceProperties governance;
	private final Clock clock;

	public ApprovalService(ApprovalRepository approvalRepository, WorkflowRunRepository workflowRunRepository,
			WorkflowTaskRepository taskRepository, DecisionRepository decisionRepository, AuditService auditService,
			SafeStopService safeStopService, GovernanceProperties governance, Clock clock) {
		this.approvalRepository = approvalRepository;
		this.workflowRunRepository = workflowRunRepository;
		this.taskRepository = taskRepository;
		this.decisionRepository = decisionRepository;
		this.auditService = auditService;
		this.safeStopService = safeStopService;
		this.governance = governance;
		this.clock = clock;
	}

	public boolean isGateEnabled(ApprovalGate gate) {
		return governance.isGateEnabled(gate);
	}

	/**
	 * Checks a gate before autonomous work continues.
	 *
	 * <p>Requests the approval the first time the gate is reached and parks the
	 * workflow (and the blocked task, if any) until a human resolves it.
	 *
	 * @return {@code true} when execution may continue
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean requireGate(WorkflowRun run, ApprovalGate gate, WorkflowTask blockedTask) {
		if (!governance.isGateEnabled(gate)) {
			return true;
		}
		Approval approval = approvalRepository
				.findByWorkflowRunIdAndGateAndGraphVersion(run.getId(), gate, run.getCurrentGraphVersion())
				.orElse(null);
		if (approval != null && approval.getStatus() == ApprovalStatus.APPROVED) {
			return true;
		}
		if (approval != null && approval.getStatus() == ApprovalStatus.REJECTED) {
			// Defensive: a rejection already safe-stopped the run; never continue.
			return false;
		}
		if (approval == null) {
			approval = approvalRepository
					.save(new Approval(run.getId(), gate, run.getCurrentGraphVersion(), clock.instant()));
			auditService.record(run.getId(), blockedTask == null ? null : blockedTask.getId(),
					AuditEventType.APPROVAL_REQUESTED,
					"Approval requested for gate " + gate + " (graph version " + run.getCurrentGraphVersion() + ")",
					ApprovalGates.describe(gate));
		}
		park(run, blockedTask);
		return false;
	}

	private void park(WorkflowRun run, WorkflowTask blockedTask) {
		if (blockedTask != null && blockedTask.getStatus() != TaskStatus.WAITING_FOR_APPROVAL) {
			blockedTask.markWaitingForApproval();
			taskRepository.save(blockedTask);
		}
		if (run.getStatus() != WorkflowStatus.WAITING_FOR_APPROVAL) {
			run.markWaitingForApproval();
			workflowRunRepository.save(run);
		}
	}

	@Transactional(readOnly = true)
	public List<Approval> approvals(UUID workflowRunId) {
		requireRun(workflowRunId);
		return approvalRepository.findByWorkflowRunIdOrderByRequestedAtAsc(workflowRunId);
	}

	/**
	 * Grants an approval and releases the workflow back to RUNNING.
	 *
	 * @return the resolved approval
	 */
	@Transactional
	public Approval approve(UUID workflowRunId, UUID approvalId, String reviewer, String comment) {
		Approval approval = requireApproval(workflowRunId, approvalId);
		Instant now = clock.instant();
		approval.approve(reviewer, comment, now);
		approvalRepository.save(approval);

		auditService.record(workflowRunId, null, AuditEventType.APPROVAL_GRANTED,
				"Gate " + approval.getGate() + " approved by " + reviewerOrAnonymous(reviewer), comment);
		decisionRepository.save(new Decision(workflowRunId, null, "APPROVAL",
				"Gate " + approval.getGate() + " approved",
				"Approved by " + reviewerOrAnonymous(reviewer)
						+ (comment == null || comment.isBlank() ? "" : ": " + comment),
				now));

		release(workflowRunId, now);
		return approval;
	}

	/**
	 * Rejects an approval. The workflow is safe-stopped rather than continued or
	 * silently failed, so the human decision stays explicit and auditable.
	 */
	@Transactional
	public Approval reject(UUID workflowRunId, UUID approvalId, String reviewer, String comment) {
		Approval approval = requireApproval(workflowRunId, approvalId);
		Instant now = clock.instant();
		approval.reject(reviewer, comment, now);
		approvalRepository.save(approval);

		auditService.record(workflowRunId, null, AuditEventType.APPROVAL_REJECTED,
				"Gate " + approval.getGate() + " rejected by " + reviewerOrAnonymous(reviewer), comment);
		decisionRepository.save(new Decision(workflowRunId, null, "APPROVAL",
				"Gate " + approval.getGate() + " rejected",
				"Rejected by " + reviewerOrAnonymous(reviewer)
						+ (comment == null || comment.isBlank() ? "" : ": " + comment),
				now));

		safeStopService.safeStop(workflowRunId, null,
				"Approval gate " + approval.getGate() + " rejected by " + reviewerOrAnonymous(reviewer));
		return approval;
	}

	/** Un-parks the run once no gate is pending any more. */
	private void release(UUID workflowRunId, Instant now) {
		if (!approvalRepository.findByWorkflowRunIdAndStatus(workflowRunId, ApprovalStatus.PENDING).isEmpty()) {
			return;
		}
		WorkflowRun run = requireRun(workflowRunId);
		if (run.getStatus() != WorkflowStatus.WAITING_FOR_APPROVAL) {
			return;
		}
		for (WorkflowTask task : taskRepository.findByWorkflowRunIdOrderBySequenceNoAsc(workflowRunId)) {
			if (task.getStatus() == TaskStatus.WAITING_FOR_APPROVAL) {
				task.markReady();
				taskRepository.save(task);
			}
		}
		run.markRunning(now);
		workflowRunRepository.save(run);
	}

	private Approval requireApproval(UUID workflowRunId, UUID approvalId) {
		Approval approval = approvalRepository.findById(approvalId)
				.orElseThrow(() -> new WorkflowNotFoundException("Approval " + approvalId + " not found"));
		if (!approval.getWorkflowRunId().equals(workflowRunId)) {
			throw new WorkflowNotFoundException(
					"Approval " + approvalId + " does not belong to workflow " + workflowRunId);
		}
		if (approval.getStatus().isResolved()) {
			throw new IllegalWorkflowStateException("Approval " + approvalId + " is already " + approval.getStatus());
		}
		return approval;
	}

	private WorkflowRun requireRun(UUID workflowRunId) {
		return workflowRunRepository.findById(workflowRunId)
				.orElseThrow(() -> new WorkflowNotFoundException("Workflow " + workflowRunId + " not found"));
	}

	private String reviewerOrAnonymous(String reviewer) {
		return reviewer == null || reviewer.isBlank() ? "unspecified operator" : reviewer;
	}
}
