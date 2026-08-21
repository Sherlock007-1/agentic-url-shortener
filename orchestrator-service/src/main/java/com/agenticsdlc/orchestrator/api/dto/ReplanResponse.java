package com.agenticsdlc.orchestrator.api.dto;

import com.agenticsdlc.orchestrator.domain.WorkflowReplan;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "ReplanResponse", description = "Lineage of a replan; the previous graph version stays queryable")
public record ReplanResponse(UUID replanId, UUID workflowId, int fromGraphVersion, int toGraphVersion, String reason,
		String previousRequirement, String newRequirement, UUID clarificationId, Instant createdAt) {

	public static ReplanResponse from(WorkflowReplan replan) {
		return new ReplanResponse(replan.getId(), replan.getWorkflowRunId(), replan.getFromGraphVersion(),
				replan.getToGraphVersion(), replan.getReason(), replan.getPreviousRequirement(),
				replan.getNewRequirement(), replan.getClarificationId(), replan.getCreatedAt());
	}
}
