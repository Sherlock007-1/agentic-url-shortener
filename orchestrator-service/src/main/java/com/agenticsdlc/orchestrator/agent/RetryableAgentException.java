package com.agenticsdlc.orchestrator.agent;

/**
 * Marks an agent failure as transient, i.e. worth another bounded attempt.
 *
 * <p>Classification is explicit on purpose: anything an agent throws that is not
 * (caused by) a {@code RetryableAgentException} is treated as a permanent failure
 * and is never retried, so a deterministic bug cannot turn into a retry loop.
 */
public class RetryableAgentException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RetryableAgentException(String message) {
		super(message);
	}

	public RetryableAgentException(String message, Throwable cause) {
		super(message, cause);
	}
}
