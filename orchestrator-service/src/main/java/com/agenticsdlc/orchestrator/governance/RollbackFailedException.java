package com.agenticsdlc.orchestrator.governance;

/** Raised when restoring a workspace from a snapshot did not succeed. */
public class RollbackFailedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RollbackFailedException(String message, Throwable cause) {
		super(message, cause);
	}
}
