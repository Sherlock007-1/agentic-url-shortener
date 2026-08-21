package com.agenticsdlc.shortener.url.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agenticsdlc.shortener.support.AbstractPostgresIntegrationTest;
import com.agenticsdlc.shortener.url.domain.ShortUrl;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Verifies the JPA mapping and the database constraints created by Flyway.
 */
@SpringBootTest
class ShortUrlRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private ShortUrlRepository repository;

	@BeforeEach
	void cleanDatabase() {
		repository.deleteAll();
	}

	@Test
	void persistsAndReadsBackAllFields() {
		Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		Instant expiresAt = createdAt.plus(7, ChronoUnit.DAYS);

		ShortUrl saved = repository.saveAndFlush(new ShortUrl("persist1", "https://example.com/a", createdAt, expiresAt));

		ShortUrl loaded = repository.findByShortCode("persist1").orElseThrow();
		assertThat(loaded.getId()).isEqualTo(saved.getId());
		assertThat(loaded.getOriginalUrl()).isEqualTo("https://example.com/a");
		assertThat(loaded.getCreatedAt()).isEqualTo(createdAt);
		assertThat(loaded.getExpiresAt()).isEqualTo(expiresAt);
		assertThat(loaded.isActive()).isTrue();
	}

	@Test
	void shortCodeIsUniqueAtTheDatabaseLevel() {
		repository.saveAndFlush(new ShortUrl("dupe1234", "https://example.com/first", Instant.now(), null));

		assertThatThrownBy(() -> repository
				.saveAndFlush(new ShortUrl("dupe1234", "https://example.com/second", Instant.now(), null)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void disableIsPersisted() {
		ShortUrl saved = repository.saveAndFlush(new ShortUrl("disab123", "https://example.com/b", Instant.now(), null));
		saved.disable();
		repository.saveAndFlush(saved);

		assertThat(repository.findById(saved.getId()).orElseThrow().isActive()).isFalse();
	}
}
