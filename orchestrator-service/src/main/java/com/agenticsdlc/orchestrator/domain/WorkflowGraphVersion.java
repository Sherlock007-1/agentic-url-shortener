package com.agenticsdlc.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable version of a workflow's task graph.
 *
 * <p>Version 1 is created together with the workflow. Keeping versions separate
 * from tasks means a future replanning increment can add version 2 without
 * mutating history.
 */
@Entity
@Table(name = "workflow_graph_versions")
public class WorkflowGraphVersion {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "workflow_run_id", nullable = false, updatable = false)
	private UUID workflowRunId;

	@Column(name = "version", nullable = false, updatable = false)
	private int version;

	@Column(name = "description", length = 255)
	private String description;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected WorkflowGraphVersion() {
		// for JPA
	}

	public WorkflowGraphVersion(UUID workflowRunId, int version, String description, Instant createdAt) {
		this.workflowRunId = workflowRunId;
		this.version = version;
		this.description = description;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getWorkflowRunId() {
		return workflowRunId;
	}

	public int getVersion() {
		return version;
	}

	public String getDescription() {
		return description;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
