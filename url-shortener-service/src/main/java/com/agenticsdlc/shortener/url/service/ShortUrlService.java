package com.agenticsdlc.shortener.url.service;

import com.agenticsdlc.shortener.url.api.dto.CreateShortUrlRequest;
import com.agenticsdlc.shortener.url.api.dto.ShortUrlAnalyticsResponse;
import com.agenticsdlc.shortener.url.api.dto.ShortUrlResponse;
import com.agenticsdlc.shortener.url.config.ShortenerProperties;
import com.agenticsdlc.shortener.url.domain.ShortUrl;
import com.agenticsdlc.shortener.url.exception.InvalidUrlException;
import com.agenticsdlc.shortener.url.exception.ShortCodeCollisionException;
import com.agenticsdlc.shortener.url.exception.ShortUrlGoneException;
import com.agenticsdlc.shortener.url.exception.ShortUrlNotFoundException;
import com.agenticsdlc.shortener.url.repository.ShortUrlRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service holding the short URL business rules.
 */
@Service
public class ShortUrlService {

	private static final Logger log = LoggerFactory.getLogger(ShortUrlService.class);

	private final ShortUrlRepository repository;
	private final ShortUrlWriter writer;
	private final ShortCodeGenerator codeGenerator;
	private final UrlValidator urlValidator;
	private final ClickAnalyticsService clickAnalyticsService;
	private final ShortenerProperties properties;
	private final Clock clock;

	public ShortUrlService(ShortUrlRepository repository, ShortUrlWriter writer, ShortCodeGenerator codeGenerator,
			UrlValidator urlValidator, ClickAnalyticsService clickAnalyticsService, ShortenerProperties properties,
			Clock clock) {
		this.repository = repository;
		this.writer = writer;
		this.codeGenerator = codeGenerator;
		this.urlValidator = urlValidator;
		this.clickAnalyticsService = clickAnalyticsService;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * Validates and stores a new short URL, retrying only on a short-code collision.
	 *
	 * <p>Generation is bounded by {@code shortener.max-code-generation-attempts}
	 * (default 3). Each attempt generates a fresh code and inserts it in its own
	 * transaction ({@link ShortUrlWriter}), so a duplicate surfaces immediately and
	 * a collision cannot poison the next attempt. The unique constraint on
	 * {@code short_urls.short_code} remains the final safety boundary - the retry
	 * only avoids failing a request for a collision that a new code would resolve.
	 *
	 * <p>Any other integrity violation is rethrown immediately and never retried.
	 * (This is a business-level retry; it is unrelated to the orchestrator's agent
	 * retry policy.)
	 *
	 * @throws ShortCodeCollisionException when every allowed attempt collided
	 */
	public ShortUrlResponse create(CreateShortUrlRequest request) {
		Instant now = clock.instant();
		String url = urlValidator.validate(request.url());
		Instant expiresAt = request.expiresAt();
		if (expiresAt != null && !expiresAt.isAfter(now)) {
			throw new InvalidUrlException("expiresAt must be in the future");
		}

		int maxAttempts = properties.maxCodeGenerationAttempts();
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			ShortUrl candidate = new ShortUrl(codeGenerator.generate(), url, now, expiresAt);
			try {
				return toResponse(writer.insert(candidate), now);
			}
			catch (DataIntegrityViolationException ex) {
				if (!ShortCodeCollisions.isShortCodeCollision(ex)) {
					// Not a short-code duplicate: a retry would only hide the real defect.
					throw ex;
				}
				log.warn("Short code collision on attempt {}/{}, generating a new code", attempt, maxAttempts);
			}
		}
		throw new ShortCodeCollisionException(maxAttempts);
	}

	/**
	 * Resolves a short code for redirection and records the click.
	 *
	 * @return the destination URL
	 * @throws ShortUrlNotFoundException when the code is unknown
	 * @throws ShortUrlGoneException     when the link is disabled or expired
	 */
	@Transactional
	public String resolveDestination(String shortCode) {
		return resolveDestination(shortCode, null);
	}

	/**
	 * Resolves a short code for redirection and records exactly one click event.
	 *
	 * <p>The click is only recorded once the link is known to be active and not
	 * expired, so unknown, disabled and expired codes never produce analytics.
	 *
	 * @param referrer optional {@code Referer} header value, may be null
	 */
	@Transactional
	public String resolveDestination(String shortCode, String referrer) {
		ShortUrl shortUrl = require(shortCode);
		if (!shortUrl.isActive()) {
			throw ShortUrlGoneException.disabled(shortCode);
		}
		Instant now = clock.instant();
		if (shortUrl.isExpiredAt(now)) {
			throw ShortUrlGoneException.expired(shortCode);
		}
		clickAnalyticsService.recordClick(shortUrl, now, referrer);
		return shortUrl.getOriginalUrl();
	}

	/**
	 * Returns metadata for a short code, including disabled and expired links so
	 * that operators can inspect why a link no longer resolves.
	 */
	@Transactional(readOnly = true)
	public ShortUrlResponse getMetadata(String shortCode) {
		return toResponse(require(shortCode), clock.instant());
	}

	/** Click analytics of a short code, including disabled and expired links. */
	@Transactional(readOnly = true)
	public ShortUrlAnalyticsResponse getAnalytics(String shortCode) {
		return clickAnalyticsService.analytics(require(shortCode));
	}

	/**
	 * Soft-deletes a short URL. Idempotent: disabling an already disabled link
	 * succeeds without changing anything.
	 */
	@Transactional
	public void disable(String shortCode) {
		ShortUrl shortUrl = require(shortCode);
		shortUrl.disable();
		repository.save(shortUrl);
	}

	private ShortUrl require(String shortCode) {
		return repository.findByShortCode(shortCode)
				.orElseThrow(() -> new ShortUrlNotFoundException(shortCode));
	}

	private ShortUrlResponse toResponse(ShortUrl shortUrl, Instant now) {
		return ShortUrlResponse.from(shortUrl, properties.baseUrl(), now);
	}
}