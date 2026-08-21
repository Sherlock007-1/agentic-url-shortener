package com.agenticsdlc.orchestrator.api.dto;

import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "WorkflowResponse", description = "Workflow run summary")
public record WorkflowResponse(UUID workflowId, UUID requirementId, String requirement, WorkflowStatus status,
		int graphVersion, String errorMessage, Instant createdAt, Instant startedAt, Instant completedAt) {

	public static WorkflowResponse from(WorkflowRun run, String requirementText) {
		return new WorkflowResponse(run.getId(), run.getRequirementId(), requirementText, run.getStatus(),
				run.getCurrentGraphVersion(), run.getErrorMessage(), run.getCreatedAt(), run.getStartedAt(),
				run.getCompletedAt());
	}
}
