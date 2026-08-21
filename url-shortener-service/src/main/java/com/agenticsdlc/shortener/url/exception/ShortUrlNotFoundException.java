package com.agenticsdlc.shortener.url.exception;

/** Thrown when no short URL exists for the requested code. Maps to HTTP 404. */
public class ShortUrlNotFoundException extends RuntimeException {

	public ShortUrlNotFoundException(String shortCode) {
		super("No short URL found for code '" + shortCode + "'");
	}
}
