package com.agenticsdlc.orchestrator.domain;

/**
 * A point in the workflow where a human must authorise further autonomous work.
 *
 * <p>Which gates are active is configuration ({@code orchestrator.governance.approval-gates}),
 * so the mechanism is generic while the demo uses the two mandated gates.
 */
public enum ApprovalGate {

	/** Architecture finished; implementation may not start before a human approves. */
	PRE_IMPLEMENTATION,

	/** Validation finished; the workflow may not be COMPLETED before a human approves. */
	FINAL
}
