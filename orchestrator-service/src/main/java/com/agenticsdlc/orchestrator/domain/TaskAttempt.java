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
 * Evidence of one bounded agent/task attempt.
 *
 * <p>This is the retry ledger of the <em>agent</em>: attempt number, whether the
 * failure was classified retryable, and whether it was the primary or the approved
 * fallback agent. Optimistic-locking retries inside the engine are a different
 * concept and are never recorded here.
 */
@Entity
@Table(name = "task_attempts")
public class TaskAttempt {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "workflow_run_id", nullable = false, updatable = false)
	private UUID workflowRunId;

	@Column(name = "task_id", nullable = false, updatable = false)
	private UUID taskId;

	@Column(name = "attempt_no", nullable = false, updatable = false)
	private int attemptNo;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, length = 16, updatable = false)
	private AttemptKind kind;

	@Enumerated(EnumType.STRING)
	@Column(name = "outcome", nullable = false, length = 16)
	private AttemptOutcome outcome;

	@Column(name = "retryable", nullable = false)
	private boolean retryable;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "started_at", nullable = false, updatable = false)
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	protected TaskAttempt() {
		// for JPA
	}

	public TaskAttempt(UUID workflowRunId, UUID taskId, int attemptNo, AttemptKind kind, Instant startedAt) {
		this.workflowRunId = workflowRunId;
		this.taskId = taskId;
		this.attemptNo = attemptNo;
		this.kind = kind;
		this.outcome = AttemptOutcome.RUNNING;
		this.retryable = false;
		this.startedAt = startedAt;
	}

	public void succeed(Instant completedAt) {
		this.outcome = AttemptOutcome.SUCCEEDED;
		this.completedAt = completedAt;
	}

	public void fail(String errorMessage, boolean retryable, Instant completedAt) {
		this.outcome = AttemptOutcome.FAILED;
		this.errorMessage = errorMessage;
		this.retryable = retryable;
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

	public int getAttemptNo() {
		return attemptNo;
	}

	public AttemptKind getKind() {
		return kind;
	}

	public AttemptOutcome getOutcome() {
		return outcome;
	}

	public boolean isRetryable() {
		return retryable;
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
