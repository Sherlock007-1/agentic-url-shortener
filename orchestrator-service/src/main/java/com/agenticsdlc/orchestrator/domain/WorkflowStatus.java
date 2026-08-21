package com.agenticsdlc.orchestrator.domain;

/**
 * Lifecycle of a workflow run.
 *
 * <p>The orchestration core defines CREATED..FAILED; the governance/recovery
 * increment adds the controlled-autonomy states. Transitions are decided by the
 * engine and the governance services (not encoded in the enum), so adding states
 * stays additive.
 *
 * <pre>
 * RUNNING --gate--&gt; WAITING_FOR_APPROVAL --approve--&gt; RUNNING
 *                                        --reject---&gt; SAFE_STOPPED
 * RUNNING --question--&gt; AWAITING_CLARIFICATION --answer--&gt; RUNNING | REPLANNING
 * RUNNING --retryable failure--&gt; RETRYING --&gt; RUNNING | SAFE_STOPPED
 * RUNNING --rollback--&gt; ROLLING_BACK --&gt; RUNNING | SAFE_STOPPED
 * REPLANNING --&gt; READY (graph version + 1)
 * </pre>
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

	/** Blocked on a human approval gate; no task may be claimed. */
	WAITING_FOR_APPROVAL,

	/** Blocked on an unanswered clarification question. */
	AWAITING_CLARIFICATION,

	/** A task failed retryably and a bounded retry is in progress. */
	RETRYING,

	/** A new graph version is being derived from a clarified/changed requirement. */
	REPLANNING,

	/** The workspace is being restored from a snapshot. */
	ROLLING_BACK,

	/** All tasks completed successfully (and every required gate was approved). */
	COMPLETED,

	/** At least one task failed in a non-retryable way. */
	FAILED,

	/**
	 * Controlled stop: an autonomy boundary was hit (retries exhausted with no
	 * approved fallback, budget exceeded, policy rejection or a rejected approval).
	 * Terminal on purpose - the orchestrator never keeps trying after a safe stop.
	 */
	SAFE_STOPPED;

	public boolean isTerminal() {
		return this == COMPLETED || this == FAILED || this == SAFE_STOPPED;
	}

	/** True while the run is parked waiting for a human decision. */
	public boolean isWaitingForHuman() {
		return this == WAITING_FOR_APPROVAL || this == AWAITING_CLARIFICATION;
	}
}