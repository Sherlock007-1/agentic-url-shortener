package com.agenticsdlc.orchestrator.domain;

/**
 * Lifecycle of a workflow run.
 *
 * <p>Only the states needed by the orchestration core are defined. Later
 * increments add states such as {@code WAITING_FOR_APPROVAL}, {@code REPLANNING},
 * {@code ROLLING_BACK} and {@code SAFE_STOPPED}; because transitions are decided
 * by the engine (and not encoded in the enum) adding them is additive.
 */
public enum WorkflowStatus {

	/** Workflow row exists, no graph yet. */
	CREATED,

	/** The graph is being built/persisted. */
	PLANNING,

	/** Graph persisted, waiting to be started. */
	READY,

	/** Tasks are being scheduled and executed. */
	RUNNING,

	/** All tasks completed successfully. */
	COMPLETED,

	/** At least one task failed. */
	FAILED;

	public boolean isTerminal() {
		return this == COMPLETED || this == FAILED;
	}
}
