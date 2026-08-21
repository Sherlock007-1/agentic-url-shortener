package com.agenticsdlc.orchestrator.domain;

/** Resolution state of a human approval gate. */
public enum ApprovalStatus {

	/** Requested by the orchestrator, no human decision yet; execution is blocked. */
	PENDING,

	APPROVED,

	REJECTED;

	public boolean isResolved() {
		return this != PENDING;
	}
}
