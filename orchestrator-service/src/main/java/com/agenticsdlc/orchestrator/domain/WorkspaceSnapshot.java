package com.agenticsdlc.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Metadata of a filesystem snapshot of the workflow workspace, plus the outcome of
 * a rollback performed from it.
 *
 * <p>Intentionally minimal: a plain directory copy under
 * {@code runs/{workflowId}/snapshots/{snapshotId}}. No content-addressed store, no
 * Git, no distributed storage.
 */
@Entity
@Table(name = "workspace_snapshots")
public class WorkspaceSnapshot {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "workflow_run_id", nullable = false, updatable = false)
	private UUID workflowRunId;

	@Column(name = "task_id", updatable = false)
	private UUID taskId;

	@Column(name = "label", nullable = false, length = 128, updatable = false)
	private String label;

	@Column(name = "location", nullable = false, updatable = false)
	private String location;

	@Column(name = "file_count", nullable = false, updatable = false)
	private int fileCount;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "rollback_status", length = 24)
	private RollbackStatus rollbackStatus;

	@Column(name = "rolled_back_at")
	private Instant rolledBackAt;

	@Column(name = "rollback_error")
	private String rollbackError;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected WorkspaceSnapshot() {
		// for JPA
	}

	public WorkspaceSnapshot(UUID workflowRunId, UUID taskId, String label, String location, int fileCount,
			Instant createdAt) {
		this.workflowRunId = workflowRunId;
		this.taskId = taskId;
		this.label = label;
		this.location = location;
		this.fileCount = fileCount;
		this.createdAt = createdAt;
	}

	public void rollbackStarted() {
		this.rollbackStatus = RollbackStatus.IN_PROGRESS;
		this.rollbackError = null;
	}

	public void rollbackCompleted(Instant rolledBackAt) {
		this.rollbackStatus = RollbackStatus.COMPLETED;
		this.rolledBackAt = rolledBackAt;
	}

	public void rollbackFailed(String error, Instant rolledBackAt) {
		this.rollbackStatus = RollbackStatus.FAILED;
		this.rollbackError = error;
		this.rolledBackAt = rolledBackAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getWorkflowRunId() {
		return workflowRunId;
	}

	public UUID getTaskId() {
		return taskId;
	}

	public String getLabel() {
		return label;
	}

	public String getLocation() {
		return location;
	}

	public int getFileCount() {
		return fileCount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public RollbackStatus getRollbackStatus() {
		return rollbackStatus;
	}

	public Instant getRolledBackAt() {
		return rolledBackAt;
	}

	public String getRollbackError() {
		return rollbackError;
	}
}
