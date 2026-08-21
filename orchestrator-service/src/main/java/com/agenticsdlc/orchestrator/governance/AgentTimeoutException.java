package com.agenticsdlc.orchestrator.governance;

import java.time.Duration;

/** Raised when a single agent attempt exceeded its configured execution budget. */
public class AgentTimeoutException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public AgentTimeoutException(String taskKey, Duration timeout) {
		super("Agent attempt for task '" + taskKey + "' exceeded the execution budget of " + timeout);
	}
}
