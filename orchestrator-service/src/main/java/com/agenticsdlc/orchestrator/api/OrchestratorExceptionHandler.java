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

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		String detail = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.reduce((a, b) -> a + "; " + b)
				.orElse("Request validation failed");
		return problem(HttpStatus.BAD_REQUEST, "Invalid request", detail);
	}

	private ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}
}
