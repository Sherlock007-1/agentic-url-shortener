package com.agenticsdlc.shortener.url.api;

import com.agenticsdlc.shortener.url.exception.InvalidUrlException;
import com.agenticsdlc.shortener.url.exception.ShortUrlGoneException;
import com.agenticsdlc.shortener.url.exception.ShortUrlNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain exceptions into RFC 7807 problem responses.
 */
@RestControllerAdvice
public class ShortUrlExceptionHandler {

	@ExceptionHandler(InvalidUrlException.class)
	public ProblemDetail handleInvalidUrl(InvalidUrlException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid URL", ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		String detail = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.reduce((a, b) -> a + "; " + b)
				.orElse("Request validation failed");
		return problem(HttpStatus.BAD_REQUEST, "Invalid request", detail);
	}

	@ExceptionHandler(ShortUrlNotFoundException.class)
	public ProblemDetail handleNotFound(ShortUrlNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Short URL not found", ex.getMessage());
	}

	@ExceptionHandler(ShortUrlGoneException.class)
	public ProblemDetail handleGone(ShortUrlGoneException ex) {
		return problem(HttpStatus.GONE, "Short URL no longer available", ex.getMessage());
	}

	/**
	 * Short-code generation is retried a bounded number of times; when every attempt
	 * collided the request fails as a conflict the caller may simply repeat.
	 */
	@ExceptionHandler(com.agenticsdlc.shortener.url.exception.ShortCodeCollisionException.class)
	public ProblemDetail handleCollision(com.agenticsdlc.shortener.url.exception.ShortCodeCollisionException ex) {
		return problem(HttpStatus.CONFLICT, "Could not generate a unique short code", ex.getMessage());
	}

	/**
	 * Short-code uniqueness is enforced by the database. Duplicates are retried in
	 * the service; anything reaching this handler is a different integrity failure.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleConflict(DataIntegrityViolationException ex) {
		return problem(HttpStatus.CONFLICT, "Could not store short URL",
				"The short URL could not be stored; please retry the request.");
	}

	private ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}
}