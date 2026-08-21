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

/** A single node of the persisted workflow graph. */
@Entity
@Table(name = "workflow_tasks")
public class WorkflowTask {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "workflow_run_id", nullable = false, updatable = false)
	private UUID workflowRunId;

	@Column(name = "graph_version_id", nullable = false, updatable = false)
	private UUID graphVersionId;

	@Column(name = "task_key", nullable = false, length = 64, updatable = false)
	private String taskKey;

	@Column(name = "name", nullable = false, length = 128, updatable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "agent_type", nullable = false, length = 48, updatable = false)
	private AgentType agentType;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private TaskStatus status;

	@Column(name = "sequence_no", nullable = false, updatable = false)
	private int sequenceNo;

	@Column(name = "input_context")
	private String inputContext;

	@Column(name = "output_context")
	private String outputContext;

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

	protected WorkflowTask() {
		// for JPA
	}

	public WorkflowTask(UUID workflowRunId, UUID graphVersionId, String taskKey, String name, AgentType agentType,
			int sequenceNo, Instant createdAt) {
		this.workflowRunId = workflowRunId;
		this.graphVersionId = graphVersionId;
		this.taskKey = taskKey;
		this.name = name;
		this.agentType = agentType;
		this.sequenceNo = sequenceNo;
		this.status = TaskStatus.PENDING;
		this.createdAt = createdAt;
	}

	public void markReady() {
		this.status = TaskStatus.READY;
	}

	public void markRunning(String inputContext, Instant startedAt) {
		this.status = TaskStatus.RUNNING;
		this.inputContext = inputContext;
		this.startedAt = startedAt;
	}

	public void markCompleted(String outputContext, Instant completedAt) {
		this.status = TaskStatus.COMPLETED;
		this.outputContext = outputContext;
		this.completedAt = completedAt;
	}

	public void markFailed(String errorMessage, Instant completedAt) {
		this.status = TaskStatus.FAILED;
		this.errorMessage = errorMessage;
		this.completedAt = completedAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getWorkflowRunId() {
		return workflowRunId;
	}

	public UUID getGraphVersionId() {
		return graphVersionId;
	}

	public String getTaskKey() {
		return taskKey;
	}

	public String getName() {
		return name;
	}

	public AgentType getAgentType() {
		return agentType;
	}

	public TaskStatus getStatus() {
		return status;
	}

	public int getSequenceNo() {
		return sequenceNo;
	}

	public String getInputContext() {
		return inputContext;
	}

	public String getOutputContext() {
		return outputContext;
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
