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
 * A question the orchestrator asks a human before it continues.
 *
 * <p>Generic on purpose: this branch implements the mechanism, the ambiguous
 * requirement scenario that uses it is a later increment.
 */
@Entity
@Table(name = "clarification_requests")
public class ClarificationRequest {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "workflow_run_id", nullable = false, updatable = false)
	private UUID workflowRunId;

	@Column(name = "task_id", updatable = false)
	private UUID taskId;

	@Column(name = "question", nullable = false, updatable = false)
	private String question;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 24)
	private ClarificationStatus status;

	@Column(name = "answer")
	private String answer;

	@Column(name = "answered_by", length = 128)
	private String answeredBy;

	@Column(name = "requested_at", nullable = false, updatable = false)
	private Instant requestedAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected ClarificationRequest() {
		// for JPA
	}

	public ClarificationRequest(UUID workflowRunId, UUID taskId, String question, Instant requestedAt) {
		this.workflowRunId = workflowRunId;
		this.taskId = taskId;
		this.question = question;
		this.status = ClarificationStatus.PENDING;
		this.requestedAt = requestedAt;
	}

	public void answer(String answer, String answeredBy, Instant resolvedAt) {
		if (this.status.isResolved()) {
			throw new IllegalStateException("Clarification " + id + " is already answered");
		}
		this.answer = answer;
		this.answeredBy = answeredBy;
		this.status = ClarificationStatus.ANSWERED;
		this.resolvedAt = resolvedAt;
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

	public String getQuestion() {
		return question;
	}

	public ClarificationStatus getStatus() {
		return status;
	}

	public String getAnswer() {
		return answer;
	}

	public String getAnsweredBy() {
		return answeredBy;
	}

	public Instant getRequestedAt() {
		return requestedAt;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}
}
