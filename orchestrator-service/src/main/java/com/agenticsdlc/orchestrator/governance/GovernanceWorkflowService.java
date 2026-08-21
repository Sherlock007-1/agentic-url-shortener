package com.agenticsdlc.orchestrator.governance;

import com.agenticsdlc.orchestrator.domain.Approval;
import com.agenticsdlc.orchestrator.domain.ClarificationRequest;
import com.agenticsdlc.orchestrator.domain.WorkflowReplan;
import com.agenticsdlc.orchestrator.engine.WorkflowEngine;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Use-case facade for the human operator actions.
 *
 * <p>Keeps the transactional governance services free of engine knowledge: each
 * method commits the governance decision first and only then asks the engine to
 * continue, so a worker thread never observes an uncommitted approval.
 */
@Service
public class GovernanceWorkflowService {

	private final ApprovalService approvalService;
	private final ClarificationService clarificationService;
	private final ReplanService replanService;
	private final WorkflowEngine engine;

	public GovernanceWorkflowService(ApprovalService approvalService, ClarificationService clarificationService,
			ReplanService replanService, WorkflowEngine engine) {
		this.approvalService = approvalService;
		this.clarificationService = clarificationService;
		this.replanService = replanService;
		this.engine = engine;
	}

	/** Approves a gate and immediately resumes the workflow. */
	public Approval approve(UUID workflowRunId, UUID approvalId, String reviewer, String comment) {
		Approval approval = approvalService.approve(workflowRunId, approvalId, reviewer, comment);
		engine.advance(workflowRunId);
		return approval;
	}

	/** Rejects a gate; the workflow is safe-stopped and never resumed. */
	public Approval reject(UUID workflowRunId, UUID approvalId, String reviewer, String comment) {
		return approvalService.reject(workflowRunId, approvalId, reviewer, comment);
	}

	/**
	 * Answers a clarification. When {@code replan} is requested the answer becomes
	 * the new requirement text and a new graph version is derived; otherwise the
	 * workflow simply continues.
	 */
	public ClarificationRequest answer(UUID workflowRunId, UUID clarificationId, String answer, String answeredBy,
			boolean replan) {
		ClarificationRequest resolved = clarificationService.answer(workflowRunId, clarificationId, answer, answeredBy);
		if (replan) {
			replanService.replan(workflowRunId, answer,
					"Clarification answered: the requirement was refined by " + reviewer(answeredBy), clarificationId);
		}
		else {
			engine.advance(workflowRunId);
		}
		return resolved;
	}

	public WorkflowReplan replan(UUID workflowRunId, String changedRequirement, String reason) {
		return replanService.replan(workflowRunId, changedRequirement, reason, null);
	}

	private String reviewer(String answeredBy) {
		return answeredBy == null || answeredBy.isBlank() ? "an unspecified operator" : answeredBy;
	}
}
