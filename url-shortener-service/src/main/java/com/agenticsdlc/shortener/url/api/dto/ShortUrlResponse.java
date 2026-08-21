package com.agenticsdlc.shortener.url.api.dto;

import com.agenticsdlc.shortener.url.domain.ShortUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Representation of a short URL returned by the API.
 */
@Schema(name = "ShortUrlResponse", description = "A shortened URL and its current state")
public record ShortUrlResponse(

		@Schema(description = "Generated short code", example = "aB3xY7z")
		String shortCode,

		@Schema(description = "Fully qualified short link", example = "http://localhost:8081/aB3xY7z")
		String shortUrl,

		@Schema(description = "Destination URL")
		String originalUrl,

		Instant createdAt,

		@Schema(description = "Expiration instant, or null when the link never expires")
		Instant expiresAt,

		@Schema(description = "False once the link has been disabled")
		boolean active,

		@Schema(description = "True when the expiration instant has passed")
		boolean expired) {

	public static ShortUrlResponse from(ShortUrl shortUrl, String baseUrl, Instant now) {
		return new ShortUrlResponse(
				shortUrl.getShortCode(),
				baseUrl + "/" + shortUrl.getShortCode(),
				shortUrl.getOriginalUrl(),
				shortUrl.getCreatedAt(),
				shortUrl.getExpiresAt(),
				shortUrl.isActive(),
				shortUrl.isExpiredAt(now));
	}
}
