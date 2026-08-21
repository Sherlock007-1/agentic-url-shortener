package com.agenticsdlc.orchestrator.engine;

/** Raised when an operation is not legal for the workflow's current state. */
public class IllegalWorkflowStateException extends RuntimeException {

	public IllegalWorkflowStateException(String message) {
		super(message);
	}
}
