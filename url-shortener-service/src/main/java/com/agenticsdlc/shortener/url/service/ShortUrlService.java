package com.agenticsdlc.shortener.url.service;

import com.agenticsdlc.shortener.url.api.dto.CreateShortUrlRequest;
import com.agenticsdlc.shortener.url.api.dto.ShortUrlResponse;
import com.agenticsdlc.shortener.url.config.ShortenerProperties;
import com.agenticsdlc.shortener.url.domain.ShortUrl;
import com.agenticsdlc.shortener.url.exception.InvalidUrlException;
import com.agenticsdlc.shortener.url.exception.ShortUrlGoneException;
import com.agenticsdlc.shortener.url.exception.ShortUrlNotFoundException;
import com.agenticsdlc.shortener.url.repository.ShortUrlRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service holding the short URL business rules.
 */
@Service
public class ShortUrlService {

	private final ShortUrlRepository repository;
	private final ShortCodeGenerator codeGenerator;
	private final UrlValidator urlValidator;
	private final ShortenerProperties properties;
	private final Clock clock;

	public ShortUrlService(ShortUrlRepository repository, ShortCodeGenerator codeGenerator, UrlValidator urlValidator,
			ShortenerProperties properties, Clock clock) {
		this.repository = repository;
		this.codeGenerator = codeGenerator;
		this.urlValidator = urlValidator;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * Validates and stores a new short URL.
	 *
	 * <p>The generated code is persisted directly; the unique constraint on
	 * {@code short_code} is the single source of truth for uniqueness.
	 */
	@Transactional
	public ShortUrlResponse create(CreateShortUrlRequest request) {
		Instant now = clock.instant();
		String url = urlValidator.validate(request.url());
		Instant expiresAt = request.expiresAt();
		if (expiresAt != null && !expiresAt.isAfter(now)) {
			throw new InvalidUrlException("expiresAt must be in the future");
		}

		ShortUrl shortUrl = new ShortUrl(codeGenerator.generate(), url, now, expiresAt);
		ShortUrl saved = repository.save(shortUrl);
		return toResponse(saved, now);
	}

	/**
	 * Resolves a short code for redirection.
	 *
	 * @return the destination URL
	 * @throws ShortUrlNotFoundException when the code is unknown
	 * @throws ShortUrlGoneException     when the link is disabled or expired
	 */
	@Transactional(readOnly = true)
	public String resolveDestination(String shortCode) {
		ShortUrl shortUrl = require(shortCode);
		if (!shortUrl.isActive()) {
			throw ShortUrlGoneException.disabled(shortCode);
		}
		if (shortUrl.isExpiredAt(clock.instant())) {
			throw ShortUrlGoneException.expired(shortCode);
		}
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
