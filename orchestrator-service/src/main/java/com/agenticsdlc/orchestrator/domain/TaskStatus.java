package com.agenticsdlc.orchestrator.domain;

/**
 * Lifecycle of a single task in the workflow graph.
 *
 * <p>{@link #RETRYING} and {@link #WAITING_FOR_APPROVAL} are non-terminal, so a
 * workflow is never finalised while a task is being retried or is parked in front
 * of a human approval gate.
 */
public enum TaskStatus {

	/** Dependencies not satisfied yet. */
	PENDING,

	/** All dependencies completed; eligible for dispatch. */
	READY,

	/** Claimed by the engine and currently executing. */
	RUNNING,

	/** A retryable agent failure occurred and another bounded attempt is scheduled. */
	RETRYING,

	/** Eligible for dispatch but blocked by an unresolved human approval gate. */
	WAITING_FOR_APPROVAL,

	/** Finished successfully. */
	COMPLETED,

	/** Finished with an error. */
	FAILED;

	public boolean isTerminal() {
		return this == COMPLETED || this == FAILED;
	}
}