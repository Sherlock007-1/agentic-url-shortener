package com.agenticsdlc.orchestrator.api.dto;

import com.agenticsdlc.orchestrator.domain.ClarificationRequest;
import com.agenticsdlc.orchestrator.domain.ClarificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "ClarificationResponse", description = "A question the orchestrator asked before continuing")
public record ClarificationResponse(UUID clarificationId, UUID workflowId, UUID taskId, String question,
		ClarificationStatus status, String answer, String answeredBy, Instant requestedAt, Instant resolvedAt) {

	public static ClarificationResponse from(ClarificationRequest request) {
		return new ClarificationResponse(request.getId(), request.getWorkflowRunId(), request.getTaskId(),
				request.getQuestion(), request.getStatus(), request.getAnswer(), request.getAnsweredBy(),
				request.getRequestedAt(), request.getResolvedAt());
	}
}
