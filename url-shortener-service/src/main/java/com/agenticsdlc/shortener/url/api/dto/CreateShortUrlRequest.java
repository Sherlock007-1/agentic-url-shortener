package com.agenticsdlc.shortener.url.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/**
 * Request payload for creating a short URL.
 *
 * <p>Structural validation of {@code url} (scheme, host, length) is performed by
 * the service layer so that the same rules apply to every caller.
 */
@Schema(name = "CreateShortUrlRequest", description = "Request to shorten a destination URL")
public record CreateShortUrlRequest(

		@Schema(description = "Destination URL (http/https only)", example = "https://example.com/some/page")
		@NotBlank(message = "url must not be blank")
		String url,

		@Schema(description = "Optional expiration instant; omit for a link that never expires",
				example = "2030-01-01T00:00:00Z")
		@Future(message = "expiresAt must be in the future")
		Instant expiresAt) {
}
