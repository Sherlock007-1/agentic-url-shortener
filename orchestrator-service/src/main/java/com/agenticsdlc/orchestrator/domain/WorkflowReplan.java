package com.agenticsdlc.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Lineage of a replan: why a new graph version was derived, from which version, and
 * what the requirement said before and after.
 *
 * <p>The previous graph version and all its tasks stay untouched and queryable.
 */
@Entity
@Table(name = "workflow_replans")
public class WorkflowReplan {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "workflow_run_id", nullable = false, updatable = false)
	private UUID workflowRunId;

	@Column(name = "from_graph_version", nullable = false, updatable = false)
	private int fromGraphVersion;

	@Column(name = "to_graph_version", nullable = false, updatable = false)
	private int toGraphVersion;

	@Column(name = "reason", nullable = false, updatable = false)
	private String reason;

	@Column(name = "previous_requirement", nullable = false, updatable = false)
	private String previousRequirement;

	@Column(name = "new_requirement", nullable = false, updatable = false)
	private String newRequirement;

	@Column(name = "clarification_id", updatable = false)
	private UUID clarificationId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected WorkflowReplan() {
		// for JPA
	}

	public WorkflowReplan(UUID workflowRunId, int fromGraphVersion, int toGraphVersion, String reason,
			String previousRequirement, String newRequirement, UUID clarificationId, Instant createdAt) {
		this.workflowRunId = workflowRunId;
		this.fromGraphVersion = fromGraphVersion;
		this.toGraphVersion = toGraphVersion;
		this.reason = reason;
		this.previousRequirement = previousRequirement;
		this.newRequirement = newRequirement;
		this.clarificationId = clarificationId;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getWorkflowRunId() {
		return workflowRunId;
	}

	public int getFromGraphVersion() {
		return fromGraphVersion;
	}

	public int getToGraphVersion() {
		return toGraphVersion;
	}

	public String getReason() {
		return reason;
	}

	public String getPreviousRequirement() {
		return previousRequirement;
	}

	public String getNewRequirement() {
		return newRequirement;
	}

	public UUID getClarificationId() {
		return clarificationId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
