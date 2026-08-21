package com.agenticsdlc.orchestrator.api.dto;

import com.agenticsdlc.orchestrator.domain.Approval;
import com.agenticsdlc.orchestrator.domain.ApprovalGate;
import com.agenticsdlc.orchestrator.domain.ApprovalStatus;
import com.agenticsdlc.orchestrator.governance.ApprovalGates;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "ApprovalResponse", description = "A persisted human approval gate")
public record ApprovalResponse(UUID approvalId, UUID workflowId, ApprovalGate gate, String description,
		int graphVersion, ApprovalStatus status, Instant requestedAt, Instant resolvedAt, String reviewer,
		String comment) {

	public static ApprovalResponse from(Approval approval) {
		return new ApprovalResponse(approval.getId(), approval.getWorkflowRunId(), approval.getGate(),
				ApprovalGates.describe(approval.getGate()), approval.getGraphVersion(), approval.getStatus(),
				approval.getRequestedAt(), approval.getResolvedAt(), approval.getReviewer(), approval.getComment());
	}
}
