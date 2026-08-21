package com.agenticsdlc.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A meaningful planning/architecture decision taken during a run, kept so the
 * reasoning behind the produced artefacts stays traceable (decision lineage).
 */
@Entity
@Table(name = "decisions")
public class Decision {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "workflow_run_id", nullable = false, updatable = false)
	private UUID workflowRunId;

	@Column(name = "task_id", updatable = false)
	private UUID taskId;

	@Column(name = "decision_type", nullable = false, length = 64, updatable = false)
	private String decisionType;

	@Column(name = "title", nullable = false, length = 255, updatable = false)
	private String title;

	@Column(name = "rationale", nullable = false, updatable = false)
	private String rationale;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Decision() {
		// for JPA
	}

	public Decision(UUID workflowRunId, UUID taskId, String decisionType, String title, String rationale,
			Instant createdAt) {
		this.workflowRunId = workflowRunId;
		this.taskId = taskId;
		this.decisionType = decisionType;
		this.title = title;
		this.rationale = rationale;
		this.createdAt = createdAt;
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

	public String getDecisionType() {
		return decisionType;
	}

	public String getTitle() {
		return title;
	}

	public String getRationale() {
		return rationale;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
