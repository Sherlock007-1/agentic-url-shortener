package com.agenticsdlc.shortener.url.service;

import com.agenticsdlc.shortener.url.domain.ShortUrl;
import com.agenticsdlc.shortener.url.repository.ShortUrlRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Performs a single short-URL insert in its own transaction.
 *
 * <p>This exists purely to make the collision retry correct. A unique-constraint
 * violation raised by PostgreSQL aborts the surrounding transaction: once it has
 * happened, no further statement may run in it. Retrying inside the same
 * transaction would therefore fail with "current transaction is aborted" instead
 * of inserting the regenerated code.
 *
 * <p>Giving every attempt its own {@code REQUIRES_NEW} transaction means a
 * collision rolls back exactly one failed insert and leaves the next attempt with
 * a clean transaction and a clean persistence context.
 */
@Component
public class ShortUrlWriter {

	private final ShortUrlRepository repository;

	public ShortUrlWriter(ShortUrlRepository repository) {
		this.repository = repository;
	}

	/**
	 * Inserts one candidate short URL and flushes immediately, so a duplicate short
	 * code surfaces here as a {@code DataIntegrityViolationException} rather than
	 * silently at commit time.
	 *
	 * @throws org.springframework.dao.DataIntegrityViolationException on any constraint violation
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ShortUrl insert(ShortUrl candidate) {
		return repository.saveAndFlush(candidate);
	}
}
