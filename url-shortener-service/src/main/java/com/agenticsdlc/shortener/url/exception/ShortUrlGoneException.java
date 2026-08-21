package com.agenticsdlc.shortener.url.exception;

/**
 * Thrown when a short URL exists but is no longer resolvable because it expired
 * or was disabled. Maps to HTTP 410 (Gone).
 */
public class ShortUrlGoneException extends RuntimeException {

	public ShortUrlGoneException(String message) {
		super(message);
	}

	public static ShortUrlGoneException expired(String shortCode) {
		return new ShortUrlGoneException("Short URL '" + shortCode + "' has expired");
	}

	public static ShortUrlGoneException disabled(String shortCode) {
		return new ShortUrlGoneException("Short URL '" + shortCode + "' has been disabled");
	}
}
