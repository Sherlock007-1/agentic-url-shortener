package com.agenticsdlc.orchestrator.api;

import com.agenticsdlc.orchestrator.engine.IllegalWorkflowStateException;
import com.agenticsdlc.orchestrator.engine.WorkflowNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps orchestration errors to RFC 7807 problem responses. */
@RestControllerAdvice
public class OrchestratorExceptionHandler {

	@ExceptionHandler(WorkflowNotFoundException.class)
	public ProblemDetail handleNotFound(WorkflowNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
	}

	@ExceptionHandler(IllegalWorkflowStateException.class)
	public ProblemDetail handleIllegalState(IllegalWorkflowStateException ex) {
		return problem(HttpStatus.CONFLICT, "Illegal workflow state", ex.getMessage());
	}

	@ExceptionHandler(com.agenticsdlc.orchestrator.governance.PolicyViolationException.class)
	public ProblemDetail handlePolicyViolation(com.agenticsdlc.orchestrator.governance.PolicyViolationException ex) {
		return problem(HttpStatus.FORBIDDEN, "Change policy violation", ex.getMessage());
	}

	@ExceptionHandler(com.agenticsdlc.orchestrator.governance.RollbackFailedException.class)
	public ProblemDetail handleRollbackFailed(com.agenticsdlc.orchestrator.governance.RollbackFailedException ex) {
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Rollback failed", ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		String detail = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.reduce((a, b) -> a + "; " + b)
				.orElse("Request validation failed");
		return problem(HttpStatus.BAD_REQUEST, "Invalid request", detail);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage());
	}

	private ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}
}