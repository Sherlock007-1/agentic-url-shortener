package com.agenticsdlc.orchestrator.governance;

/**
 * Raised when a requested mutation is outside the approved change policy.
 *
 * <p>The action is prevented, audited and - when it happened inside a workflow -
 * leads to a controlled safe stop.
 */
public class PolicyViolationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public PolicyViolationException(String message) {
		super(message);
	}
}
