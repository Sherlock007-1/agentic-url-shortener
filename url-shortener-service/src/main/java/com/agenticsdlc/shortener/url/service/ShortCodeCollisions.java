package com.agenticsdlc.shortener.url.service;

import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Recognises the one integrity failure that may be retried: a duplicate short code.
 *
 * <p>The check is deliberately narrow. Any other integrity violation (a bug, a
 * different constraint, a corrupted payload) must surface immediately instead of
 * being masked by a retry loop.
 */
final class ShortCodeCollisions {

	/** Unique constraint from {@code V2__short_urls.sql}. */
	static final String CONSTRAINT = "uk_short_urls_short_code";

	private static final String COLUMN = "short_code";

	private ShortCodeCollisions() {
	}

	static boolean isShortCodeCollision(DataIntegrityViolationException exception) {
		for (Throwable current = exception; current != null; current = current.getCause()) {
			String message = current.getMessage();
			if (message != null) {
				String lower = message.toLowerCase(Locale.ROOT);
				if (lower.contains(CONSTRAINT) || (lower.contains(COLUMN) && lower.contains("unique"))
						|| (lower.contains(COLUMN) && lower.contains("duplicate"))) {
					return true;
				}
			}
			if (current.getCause() == current) {
				break;
			}
		}
		return false;
	}
}
