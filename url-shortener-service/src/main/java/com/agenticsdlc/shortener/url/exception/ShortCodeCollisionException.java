package com.agenticsdlc.shortener.url.exception;

/**
 * Raised when short-code generation collided on every allowed attempt.
 *
 * <p>Distinct from a generic integrity violation on purpose: it means "the bounded
 * retry budget is used up", which is a conflict the caller may simply retry.
 */
public class ShortCodeCollisionException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final int attempts;

	public ShortCodeCollisionException(int attempts) {
		super("Could not generate a unique short code after " + attempts + " attempts");
		this.attempts = attempts;
	}

	public int getAttempts() {
		return attempts;
	}
}
