package com.agenticsdlc.orchestrator.domain;

/** Resolution state of a clarification question asked by the orchestrator. */
public enum ClarificationStatus {

	/** Asked, not answered yet; the workflow is parked. */
	PENDING,

	ANSWERED;

	public boolean isResolved() {
		return this != PENDING;
	}
}
