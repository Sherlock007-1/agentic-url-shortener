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
 * A shortened URL.
 *
 * <p>Rows are never deleted by the API: "delete" is modelled as a soft disable so
 * that previously issued short links stop resolving without the code being reused.
 */
@Entity
@Table(name = "short_urls")
public class ShortUrl {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "short_code", nullable = false, length = 16, updatable = false, unique = true)
	private String shortCode;

	@Column(name = "original_url", nullable = false, length = 2048, updatable = false)
	private String originalUrl;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	protected ShortUrl() {
		// for JPA
	}

	public ShortUrl(String shortCode, String originalUrl, Instant createdAt, Instant expiresAt) {
		this.shortCode = Objects.requireNonNull(shortCode, "shortCode");
		this.originalUrl = Objects.requireNonNull(originalUrl, "originalUrl");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		this.expiresAt = expiresAt;
		this.active = true;
	}

	/** Marks this short URL as no longer resolvable. Idempotent. */
	public void disable() {
		this.active = false;
	}

	public boolean isExpiredAt(Instant now) {
		return expiresAt != null && !expiresAt.isAfter(now);
	}

	public Long getId() {
		return id;
	}

	public String getShortCode() {
		return shortCode;
	}

	public String getOriginalUrl() {
		return originalUrl;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public boolean isActive() {
		return active;
	}
}
