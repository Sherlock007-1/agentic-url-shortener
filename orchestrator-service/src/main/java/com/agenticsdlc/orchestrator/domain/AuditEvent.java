package com.agenticsdlc.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit trail entry. The generated identity column also provides a
 * stable ordering that does not depend on clock resolution.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "workflow_run_id", nullable = false, updatable = false)
	private UUID workflowRunId;

	@Column(name = "task_id", updatable = false)
	private UUID taskId;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 48, updatable = false)
	private AuditEventType eventType;

	@Column(name = "message", nullable = false, length = 512, updatable = false)
	private String message;

	@Column(name = "details", updatable = false)
	private String details;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AuditEvent() {
		// for JPA
	}

	public AuditEvent(UUID workflowRunId, UUID taskId, AuditEventType eventType, String message, String details,
			Instant createdAt) {
		this.workflowRunId = workflowRunId;
		this.taskId = taskId;
		this.eventType = eventType;
		this.message = message;
		this.details = details;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public UUID getWorkflowRunId() {
		return workflowRunId;
	}

	public UUID getTaskId() {
		return taskId;
	}

	public AuditEventType getEventType() {
		return eventType;
	}

	public String getMessage() {
		return message;
	}

	public String getDetails() {
		return details;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
