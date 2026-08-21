package com.agenticsdlc.shortener.url.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The retry decision must be narrow: only a duplicate short code may be retried.
 */
class ShortCodeCollisionsTest {

	@Test
	void recognisesTheNamedUniqueConstraint() {
		assertThat(ShortCodeCollisions.isShortCodeCollision(new DataIntegrityViolationException(
				"duplicate key value violates unique constraint \"" + ShortCodeCollisions.CONSTRAINT + "\"")))
				.isTrue();
	}

	@Test
	void recognisesACollisionReportedInANestedCause() {
		DataIntegrityViolationException exception = new DataIntegrityViolationException("could not execute statement",
				new IllegalStateException("ERROR: duplicate key value for short_code"));

		assertThat(ShortCodeCollisions.isShortCodeCollision(exception)).isTrue();
	}

	@Test
	void doesNotRecogniseAnUnrelatedConstraintViolation() {
		assertThat(ShortCodeCollisions.isShortCodeCollision(new DataIntegrityViolationException(
				"null value in column \"original_url\" violates not-null constraint"))).isFalse();
	}

	@Test
	void doesNotRecogniseADifferentUniqueConstraint() {
		assertThat(ShortCodeCollisions.isShortCodeCollision(new DataIntegrityViolationException(
				"duplicate key value violates unique constraint \"uk_users_email\""))).isFalse();
	}

	@Test
	void toleratesAnExceptionWithoutAMessage() {
		assertThat(ShortCodeCollisions.isShortCodeCollision(new DataIntegrityViolationException(null))).isFalse();
	}
}
