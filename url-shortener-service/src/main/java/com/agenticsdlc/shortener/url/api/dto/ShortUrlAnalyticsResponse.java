package com.agenticsdlc.shortener.url.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Click analytics for one short URL.
 *
 * @param shortCode    the code the clicks belong to
 * @param totalClicks  number of successful redirects ever served for this code
 * @param lastClickedAt moment of the most recent successful redirect, null when unused
 * @param recentClicks  a small window of the most recent clicks, newest first
 */
@Schema(name = "ShortUrlAnalyticsResponse", description = "Click analytics derived from persisted click events")
public record ShortUrlAnalyticsResponse(String shortCode, long totalClicks, Instant lastClickedAt,
		List<ClickResponse> recentClicks) {

	/**
	 * A single recorded click. Only the time and the optional referrer are stored -
	 * no IP address, user agent or location.
	 */
	@Schema(name = "ClickResponse", description = "A single recorded click")
	public record ClickResponse(Instant clickedAt, String referrer) {
	}
}
