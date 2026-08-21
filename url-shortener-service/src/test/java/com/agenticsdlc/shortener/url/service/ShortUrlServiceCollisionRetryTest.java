package com.agenticsdlc.shortener.url.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agenticsdlc.shortener.url.api.dto.CreateShortUrlRequest;
import com.agenticsdlc.shortener.url.api.dto.ShortUrlResponse;
import com.agenticsdlc.shortener.url.config.ShortenerProperties;
import com.agenticsdlc.shortener.url.domain.ShortUrl;
import com.agenticsdlc.shortener.url.exception.ShortCodeCollisionException;
import com.agenticsdlc.shortener.url.repository.ShortUrlRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Brownfield increment: short-code generation is collision-safe.
 *
 * <p>The generator is stubbed with a fixed sequence of codes and the repository is
 * scripted to reject the codes that are supposed to be "already taken", so every
 * assertion below is deterministic - no randomness and no reliance on an actual
 * hash collision ever occurring.
 */
@ExtendWith(MockitoExtension.class)
class ShortUrlServiceCollisionRetryTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	private static final CreateShortUrlRequest REQUEST = new CreateShortUrlRequest("https://example.com/page", null);

	@Mock
	private ShortUrlRepository repository;

	@Mock
	private ShortUrlWriter writer;

	@Mock
	private ClickAnalyticsService clickAnalyticsService;

	private final QueuedCodeGenerator codeGenerator = new QueuedCodeGenerator();

	private ShortUrlService service() {
		ShortenerProperties properties = new ShortenerProperties("http://localhost:8081", 7, 2048, 3);
		return new ShortUrlService(repository, writer, codeGenerator, new UrlValidator(properties),
				clickAnalyticsService, properties, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void aSingleCollisionIsResolvedBySecondGeneratedCode() {
		codeGenerator.queue("taken01", "free001");
		rejectCodes(Set.of("taken01"));

		ShortUrlResponse response = service().create(REQUEST);

		assertThat(response.shortCode()).isEqualTo("free001");
		assertThat(codeGenerator.generatedCodes()).containsExactly("taken01", "free001");
		verify(writer, times(2)).insert(any(ShortUrl.class));
	}

	@Test
	void twoCollisionsAreResolvedByTheThirdGeneratedCode() {
		codeGenerator.queue("taken01", "taken02", "free001");
		rejectCodes(Set.of("taken01", "taken02"));

		ShortUrlResponse response = service().create(REQUEST);

		assertThat(response.shortCode()).isEqualTo("free001");
		assertThat(codeGenerator.generatedCodes()).containsExactly("taken01", "taken02", "free001");
		verify(writer, times(3)).insert(any(ShortUrl.class));
	}

	@Test
	void threeCollisionsFailAfterExactlyThreeAttempts() {
		codeGenerator.queue("taken01", "taken02", "taken03", "never01");
		rejectCodes(Set.of("taken01", "taken02", "taken03"));

		assertThatThrownBy(() -> service().create(REQUEST))
				.isInstanceOf(ShortCodeCollisionException.class)
				.hasMessageContaining("3 attempts")
				.extracting(ex -> ((ShortCodeCollisionException) ex).getAttempts())
				.isEqualTo(3);

		// Bounded, not infinite: the fourth queued code is never used.
		assertThat(codeGenerator.generatedCodes()).containsExactly("taken01", "taken02", "taken03");
		verify(writer, times(3)).insert(any(ShortUrl.class));
	}

	@Test
	void anUnrelatedIntegrityViolationIsNeverRetried() {
		codeGenerator.queue("free001", "free002", "free003");
		when(writer.insert(any(ShortUrl.class))).thenThrow(new DataIntegrityViolationException(
				"null value in column \"original_url\" of relation \"short_urls\" violates not-null constraint"));

		assertThatThrownBy(() -> service().create(REQUEST))
				.isInstanceOf(DataIntegrityViolationException.class)
				.isNotInstanceOf(ShortCodeCollisionException.class);

		assertThat(codeGenerator.generatedCodes()).containsExactly("free001");
		verify(writer, times(1)).insert(any(ShortUrl.class));
	}

	@Test
	void aFirstAttemptSuccessStillBehavesLikeTheBaseline() {
		codeGenerator.queue("free001");
		rejectCodes(Set.of());

		ShortUrlResponse response = service().create(REQUEST);

		assertThat(response.shortCode()).isEqualTo("free001");
		assertThat(response.shortUrl()).isEqualTo("http://localhost:8081/free001");
		assertThat(codeGenerator.generatedCodes()).containsExactly("free001");
		verify(writer, times(1)).insert(any(ShortUrl.class));
	}

	/** Scripts the writer: the listed codes behave as if they already exist. */
	private void rejectCodes(Set<String> taken) {
		when(writer.insert(any(ShortUrl.class))).thenAnswer(invocation -> {
			ShortUrl candidate = invocation.getArgument(0);
			if (taken.contains(candidate.getShortCode())) {
				throw new DataIntegrityViolationException(
						"could not execute statement [ERROR: duplicate key value violates unique constraint \""
								+ ShortCodeCollisions.CONSTRAINT + "\"]");
			}
			return candidate;
		});
	}

	/** Deterministic replacement for the SecureRandom-backed generator. */
	private static final class QueuedCodeGenerator extends ShortCodeGenerator {

		private final Deque<String> queued = new ArrayDeque<>();
		private final List<String> generated = new ArrayList<>();

		private QueuedCodeGenerator() {
			super(new ShortenerProperties("http://localhost:8081", 7, 2048, 3));
		}

		void queue(String... codes) {
			queued.addAll(List.of(codes));
		}

		List<String> generatedCodes() {
			return List.copyOf(generated);
		}

		@Override
		public String generate() {
			String code = queued.poll();
			if (code == null) {
				throw new IllegalStateException("The service asked for more codes than the test scripted");
			}
			generated.add(code);
			return code;
		}
	}
}
