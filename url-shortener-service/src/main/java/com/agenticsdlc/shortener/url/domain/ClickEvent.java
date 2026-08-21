package com.agenticsdlc.shortener.url.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * One successful resolution of a short URL.
 *
 * <p>Deliberately minimal: the click time and an optional referrer are enough for
 * click counts. No IP address, user agent or location is captured, so the feature
 * needs no personal-data handling beyond the referrer the browser volunteers.
 */
@Entity
@Table(name = "click_events")
public class ClickEvent {

	/** Referrer values are truncated to the column width instead of failing a redirect. */
	public static final int MAX_REFERRER_LENGTH = 512;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "short_url_id", nullable = false, updatable = false)
	private Long shortUrlId;

	@Column(name = "clicked_at", nullable = false, updatable = false)
	private Instant clickedAt;

	@Column(name = "referrer", length = MAX_REFERRER_LENGTH, updatable = false)
	private String referrer;

	protected ClickEvent() {
		// for JPA
	}

	public ClickEvent(Long shortUrlId, Instant clickedAt, String referrer) {
		this.shortUrlId = Objects.requireNonNull(shortUrlId, "shortUrlId");
		this.clickedAt = Objects.requireNonNull(clickedAt, "clickedAt");
		this.referrer = normalise(referrer);
	}

	private static String normalise(String referrer) {
		if (referrer == null || referrer.isBlank()) {
			return null;
		}
		String trimmed = referrer.trim();
		return trimmed.length() <= MAX_REFERRER_LENGTH ? trimmed : trimmed.substring(0, MAX_REFERRER_LENGTH);
	}

	public Long getId() {
		return id;
	}

	public Long getShortUrlId() {
		return shortUrlId;
	}

	public Instant getClickedAt() {
		return clickedAt;
	}

	public String getReferrer() {
		return referrer;
	}
}
