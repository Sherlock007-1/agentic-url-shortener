package com.agenticsdlc.shortener.url.exception;

/** Thrown when a submitted destination URL fails validation. Maps to HTTP 400. */
public class InvalidUrlException extends RuntimeException {

	public InvalidUrlException(String message) {
		super(message);
	}
}
