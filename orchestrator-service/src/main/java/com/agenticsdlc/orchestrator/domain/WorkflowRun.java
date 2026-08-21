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
 * One execution of the SDLC workflow for a requirement.
 *
 * <p>Carries an optimistic-locking version so concurrent task completions cannot
 * silently overwrite each other's status transitions.
 */
@Entity
@Table(name = "workflow_runs")
public class WorkflowRun {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "requirement_id", nullable = false, updatable = false)
	private UUID requirementId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private WorkflowStatus status;

	@Column(name = "current_graph_version", nullable = false)
	private int currentGraphVersion;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected WorkflowRun() {
		// for JPA
	}

	public WorkflowRun(UUID requirementId, Instant createdAt) {
		this.requirementId = requirementId;
		this.status = WorkflowStatus.CREATED;
		this.currentGraphVersion = 0;
		this.createdAt = createdAt;
	}

	public void markPlanning() {
		this.status = WorkflowStatus.PLANNING;
	}

	public void markReady(int graphVersion) {
		this.status = WorkflowStatus.READY;
		this.currentGraphVersion = graphVersion;
	}

	public void markRunning(Instant startedAt) {
		this.status = WorkflowStatus.RUNNING;
		if (this.startedAt == null) {
			this.startedAt = startedAt;
		}
	}

	public void markCompleted(Instant completedAt) {
		this.status = WorkflowStatus.COMPLETED;
		this.completedAt = completedAt;
	}

	public void markFailed(String errorMessage, Instant completedAt) {
		this.status = WorkflowStatus.FAILED;
		this.errorMessage = errorMessage;
		this.completedAt = completedAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getRequirementId() {
		return requirementId;
	}

	public WorkflowStatus getStatus() {
		return status;
	}

	public int getCurrentGraphVersion() {
		return currentGraphVersion;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}
}
