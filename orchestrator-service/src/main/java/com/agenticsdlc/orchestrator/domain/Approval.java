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
 * Persisted human approval gate.
 *
 * <p>The row is the evidence: it records which gate was requested for which graph
 * version, when it was requested, who resolved it, how and why.
 */
@Entity
@Table(name = "approvals")
public class Approval {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "workflow_run_id", nullable = false, updatable = false)
	private UUID workflowRunId;

	@Enumerated(EnumType.STRING)
	@Column(name = "gate", nullable = false, length = 48, updatable = false)
	private ApprovalGate gate;

	@Column(name = "graph_version", nullable = false, updatable = false)
	private int graphVersion;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 24)
	private ApprovalStatus status;

	@Column(name = "requested_at", nullable = false, updatable = false)
	private Instant requestedAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Column(name = "reviewer", length = 128)
	private String reviewer;

	@Column(name = "comment")
	private String comment;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected Approval() {
		// for JPA
	}

	public Approval(UUID workflowRunId, ApprovalGate gate, int graphVersion, Instant requestedAt) {
		this.workflowRunId = workflowRunId;
		this.gate = gate;
		this.graphVersion = graphVersion;
		this.status = ApprovalStatus.PENDING;
		this.requestedAt = requestedAt;
	}

	public void approve(String reviewer, String comment, Instant resolvedAt) {
		resolve(ApprovalStatus.APPROVED, reviewer, comment, resolvedAt);
	}

	public void reject(String reviewer, String comment, Instant resolvedAt) {
		resolve(ApprovalStatus.REJECTED, reviewer, comment, resolvedAt);
	}

	private void resolve(ApprovalStatus target, String reviewer, String comment, Instant resolvedAt) {
		if (this.status.isResolved()) {
			throw new IllegalStateException("Approval " + id + " is already " + this.status);
		}
		this.status = target;
		this.reviewer = reviewer;
		this.comment = comment;
		this.resolvedAt = resolvedAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getWorkflowRunId() {
		return workflowRunId;
	}

	public ApprovalGate getGate() {
		return gate;
	}

	public int getGraphVersion() {
		return graphVersion;
	}

	public ApprovalStatus getStatus() {
		return status;
	}

	public Instant getRequestedAt() {
		return requestedAt;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}

	public String getReviewer() {
		return reviewer;
	}

	public String getComment() {
		return comment;
	}
}
