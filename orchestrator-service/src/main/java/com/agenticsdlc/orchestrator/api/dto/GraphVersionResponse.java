package com.agenticsdlc.orchestrator.api.dto;

import com.agenticsdlc.orchestrator.domain.WorkflowGraphVersion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "GraphVersionResponse", description = "One persisted graph version of a workflow")
public record GraphVersionResponse(UUID graphVersionId, int version, String description, Instant createdAt,
		int taskCount, boolean current) {

	public static GraphVersionResponse from(WorkflowGraphVersion version, int taskCount, boolean current) {
		return new GraphVersionResponse(version.getId(), version.getVersion(), version.getDescription(),
				version.getCreatedAt(), taskCount, current);
	}
}
