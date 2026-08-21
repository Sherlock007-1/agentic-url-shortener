package com.agenticsdlc.orchestrator.api;

import com.agenticsdlc.orchestrator.api.dto.ApprovalDecisionRequest;
import com.agenticsdlc.orchestrator.api.dto.ApprovalResponse;
import com.agenticsdlc.orchestrator.api.dto.ClarificationAnswerRequest;
import com.agenticsdlc.orchestrator.api.dto.ClarificationResponse;
import com.agenticsdlc.orchestrator.api.dto.ReplanRequest;
import com.agenticsdlc.orchestrator.api.dto.ReplanResponse;
import com.agenticsdlc.orchestrator.governance.ApprovalService;
import com.agenticsdlc.orchestrator.governance.ClarificationService;
import com.agenticsdlc.orchestrator.governance.GovernanceWorkflowService;
import com.agenticsdlc.orchestrator.governance.ReplanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Human-in-the-loop APIs: approval gates, clarification gates and replanning.
 *
 * <p>Swagger is the operator console for this assessment; there is no
 * authentication, so the reviewer name is free text that is persisted as-is.
 */
@RestController
@RequestMapping("/api/workflows/{workflowId}")
@Tag(name = "Governance", description = "Approve/reject gates, answer clarifications and trigger replanning")
public class GovernanceController {

	private final ApprovalService approvalService;
	private final ClarificationService clarificationService;
	private final ReplanService replanService;
	private final GovernanceWorkflowService governanceWorkflowService;

	public GovernanceController(ApprovalService approvalService, ClarificationService clarificationService,
			ReplanService replanService, GovernanceWorkflowService governanceWorkflowService) {
		this.approvalService = approvalService;
		this.clarificationService = clarificationService;
		this.replanService = replanService;
		this.governanceWorkflowService = governanceWorkflowService;
	}

	@GetMapping("/approvals")
	@Operation(summary = "Approval gates of a workflow with their persisted status")
	public List<ApprovalResponse> approvals(@PathVariable UUID workflowId) {
		return approvalService.approvals(workflowId).stream().map(ApprovalResponse::from).toList();
	}

	@PostMapping("/approvals/{approvalId}/approve")
	@Operation(summary = "Approve a gate; the workflow resumes from persisted state")
	public ApprovalResponse approve(@PathVariable UUID workflowId, @PathVariable UUID approvalId,
			@Valid @RequestBody(required = false) ApprovalDecisionRequest request) {
		ApprovalDecisionRequest decision = request == null ? new ApprovalDecisionRequest(null, null) : request;
		return ApprovalResponse.from(governanceWorkflowService.approve(workflowId, approvalId, decision.reviewer(),
				decision.comment()));
	}

	@PostMapping("/approvals/{approvalId}/reject")
	@Operation(summary = "Reject a gate; the workflow is safe-stopped with an auditable reason")
	public ApprovalResponse reject(@PathVariable UUID workflowId, @PathVariable UUID approvalId,
			@Valid @RequestBody(required = false) ApprovalDecisionRequest request) {
		ApprovalDecisionRequest decision = request == null ? new ApprovalDecisionRequest(null, null) : request;
		return ApprovalResponse.from(governanceWorkflowService.reject(workflowId, approvalId, decision.reviewer(),
				decision.comment()));
	}

	@GetMapping("/clarifications")
	@Operation(summary = "Clarification questions asked by the orchestrator")
	public List<ClarificationResponse> clarifications(@PathVariable UUID workflowId) {
		return clarificationService.clarifications(workflowId).stream().map(ClarificationResponse::from).toList();
	}

	@PostMapping("/clarifications/{clarificationId}/answer")
	@Operation(summary = "Answer a clarification; optionally replan from the answer")
	public ClarificationResponse answer(@PathVariable UUID workflowId, @PathVariable UUID clarificationId,
			@Valid @RequestBody ClarificationAnswerRequest request) {
		return ClarificationResponse.from(governanceWorkflowService.answer(workflowId, clarificationId,
				request.answer(), request.answeredBy(), request.replan()));
	}

	@PostMapping("/replan")
	@Operation(summary = "Create the next graph version; earlier versions stay persisted and queryable")
	public ReplanResponse replan(@PathVariable UUID workflowId, @Valid @RequestBody ReplanRequest request) {
		return ReplanResponse
				.from(governanceWorkflowService.replan(workflowId, request.changedRequirement(), request.reason()));
	}

	@GetMapping("/replans")
	@Operation(summary = "Replan lineage: from/to graph version, reason and requirement change")
	public List<ReplanResponse> replans(@PathVariable UUID workflowId) {
		return replanService.history(workflowId).stream().map(ReplanResponse::from).toList();
	}
}
