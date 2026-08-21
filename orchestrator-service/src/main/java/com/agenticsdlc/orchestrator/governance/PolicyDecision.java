package com.agenticsdlc.orchestrator.governance;

/**
 * Outcome of a change-policy check.
 *
 * @param allowed whether the mutation may proceed
 * @param reason  why it was rejected (null when allowed)
 */
public record PolicyDecision(boolean allowed, String reason) {

	public static PolicyDecision allow() {
		return new PolicyDecision(true, null);
	}

	public static PolicyDecision reject(String reason) {
		return new PolicyDecision(false, reason);
	}

	public void throwIfRejected() {
		if (!allowed) {
			throw new PolicyViolationException(reason);
		}
	}
}
