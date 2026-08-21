package com.agenticsdlc.orchestrator.api.dto;

import com.agenticsdlc.orchestrator.domain.RollbackStatus;
import com.agenticsdlc.orchestrator.domain.WorkspaceSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "SnapshotResponse", description = "Workspace snapshot metadata and rollback outcome")
public record SnapshotResponse(UUID snapshotId, UUID workflowId, UUID taskId, String label, String location,
		int fileCount, Instant createdAt, RollbackStatus rollbackStatus, Instant rolledBackAt, String rollbackError) {

	public static SnapshotResponse from(WorkspaceSnapshot snapshot) {
		return new SnapshotResponse(snapshot.getId(), snapshot.getWorkflowRunId(), snapshot.getTaskId(),
				snapshot.getLabel(), snapshot.getLocation(), snapshot.getFileCount(), snapshot.getCreatedAt(),
				snapshot.getRollbackStatus(), snapshot.getRolledBackAt(), snapshot.getRollbackError());
	}
}
