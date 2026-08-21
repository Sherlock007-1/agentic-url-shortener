package com.agenticsdlc.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Record of one agent invocation for a task, including the context it received
 * and the output it produced. This is what makes an execution auditable.
 */
@Entity
@Table(name = "agent_executions")
public class AgentExecution {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "workflow_run_id", nullable = false, updatable = false)
	private UUID workflowRunId;

	@Column(name = "task_id", nullable = false, updatable = false)
	private UUID taskId;

	@Enumerated(EnumType.STRING)
	@Column(name = "agent_type", nullable = false, length = 48, updatable = false)
	private AgentType agentType;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private TaskStatus status;

	@Column(name = "input_context", updatable = false)
	private String inputContext;

	@Column(name = "output")
	private String output;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "started_at", nullable = false, updatable = false)
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	protected AgentExecution() {
		// for JPA
	}

	public AgentExecution(UUID workflowRunId, UUID taskId, AgentType agentType, String inputContext,
			Instant startedAt) {
		this.workflowRunId = workflowRunId;
		this.taskId = taskId;
		this.agentType = agentType;
		this.inputContext = inputContext;
		this.status = TaskStatus.RUNNING;
		this.startedAt = startedAt;
	}

	public void succeed(String output, Instant completedAt) {
		this.status = TaskStatus.COMPLETED;
		this.output = output;
		this.completedAt = completedAt;
	}

	public void fail(String errorMessage, Instant completedAt) {
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

	public UUID getTaskId() {
		return taskId;
	}

	public AgentType getAgentType() {
		return agentType;
	}

	public TaskStatus getStatus() {
		return status;
	}

	public String getInputContext() {
		return inputContext;
	}

	public String getOutput() {
		return output;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}
}
