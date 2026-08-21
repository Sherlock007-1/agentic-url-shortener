package com.agenticsdlc.orchestrator.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "GraphResponse", description = "Persisted workflow graph: nodes, edges and current statuses")
public record GraphResponse(UUID workflowId, int version, String description, Instant createdAt,
		List<TaskResponse> tasks) {
}
