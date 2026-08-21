package com.agenticsdlc.orchestrator.domain;

/**
 * Lifecycle of a single task in the workflow graph.
 *
 * <p>Later increments add {@code RETRYING}, {@code WAITING_FOR_APPROVAL} and
 * {@code SKIPPED} without changing the meaning of the states below.
 */
public enum TaskStatus {

	/** Dependencies not satisfied yet. */
	PENDING,

	/** All dependencies completed; eligible for dispatch. */
	READY,

	/** Claimed by the engine and currently executing. */
	RUNNING,

	/** Finished successfully. */
	COMPLETED,

	/** Finished with an error. */
	FAILED;

	public boolean isTerminal() {
		return this == COMPLETED || this == FAILED;
	}
}
