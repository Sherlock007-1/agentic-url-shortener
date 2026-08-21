package com.agenticsdlc.orchestrator.engine;

/** Raised when the requested workflow or task does not exist. */
public class WorkflowNotFoundException extends RuntimeException {

	public WorkflowNotFoundException(String message) {
		super(message);
	}
}
