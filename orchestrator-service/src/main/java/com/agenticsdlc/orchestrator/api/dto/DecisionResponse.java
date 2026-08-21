package com.agenticsdlc.orchestrator.api.dto;

import com.agenticsdlc.orchestrator.domain.Decision;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "DecisionResponse", description = "A recorded planning/architecture decision")
public record DecisionResponse(UUID decisionId, UUID taskId, String decisionType, String title, String rationale,
		Instant createdAt) {

	public static DecisionResponse from(Decision decision) {
		return new DecisionResponse(decision.getId(), decision.getTaskId(), decision.getDecisionType(),
				decision.getTitle(), decision.getRationale(), decision.getCreatedAt());
	}
}
