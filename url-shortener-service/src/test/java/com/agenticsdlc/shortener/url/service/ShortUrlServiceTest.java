package com.agenticsdlc.shortener.url.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShortUrlServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	@Mock
	private ShortUrlRepository repository;

	/** Deterministic generator stub; no mocking framework needed for a value producer. */
	private final ShortCodeGenerator codeGenerator =
			new ShortCodeGenerator(new ShortenerProperties("http://localhost:8081", 7, 2048)) {
				@Override
				public String generate() {
					return "abc1234";
				}
			};

	private ShortUrlService service;

	@BeforeEach
	void setUp() {
		ShortenerProperties properties = new ShortenerProperties("http://localhost:8081", 7, 2048);
		service = new ShortUrlService(repository, codeGenerator, new UrlValidator(properties), properties,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void createStoresValidatedUrlWithGeneratedCode() {
		when(repository.save(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Instant expiresAt = NOW.plusSeconds(3600);
		ShortUrlResponse response = service.create(new CreateShortUrlRequest("https://example.com/page", expiresAt));

		ArgumentCaptor<ShortUrl> captor = ArgumentCaptor.forClass(ShortUrl.class);
		verify(repository).save(captor.capture());
		ShortUrl saved = captor.getValue();
		assertThat(saved.getShortCode()).isEqualTo("abc1234");
		assertThat(saved.getOriginalUrl()).isEqualTo("https://example.com/page");
		assertThat(saved.getCreatedAt()).isEqualTo(NOW);
		assertThat(saved.getExpiresAt()).isEqualTo(expiresAt);
		assertThat(saved.isActive()).isTrue();

		assertThat(response.shortCode()).isEqualTo("abc1234");
		assertThat(response.shortUrl()).isEqualTo("http://localhost:8081/abc1234");
		assertThat(response.expired()).isFalse();
	}

	@Test
	void createRejectsInvalidDestinationUrl() {
		assertThatThrownBy(() -> service.create(new CreateShortUrlRequest("javascript:alert(1)", null)))
				.isInstanceOf(InvalidUrlException.class);
		verify(repository, never()).save(any());
	}

	@Test
	void createRejectsExpirationInThePast() {
		assertThatThrownBy(() -> service.create(
				new CreateShortUrlRequest("https://example.com", NOW.minusSeconds(1))))
				.isInstanceOf(InvalidUrlException.class)
				.hasMessageContaining("expiresAt");
		verify(repository, never()).save(any());
	}

	@Test
	void resolveReturnsDestinationForActiveUrl() {
		when(repository.findByShortCode("abc1234"))
				.thenReturn(Optional.of(new ShortUrl("abc1234", "https://example.com", NOW, null)));

		assertThat(service.resolveDestination("abc1234")).isEqualTo("https://example.com");
	}

	@Test
	void resolveFailsForUnknownCode() {
		when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.resolveDestination("missing"))
				.isInstanceOf(ShortUrlNotFoundException.class);
	}

	@Test
	void resolveFailsForExpiredUrl() {
		when(repository.findByShortCode("expired1"))
				.thenReturn(Optional.of(new ShortUrl("expired1", "https://example.com", NOW.minusSeconds(60),
						NOW.minusSeconds(1))));

		assertThatThrownBy(() -> service.resolveDestination("expired1"))
				.isInstanceOf(ShortUrlGoneException.class)
				.hasMessageContaining("expired");
	}

	@Test
	void resolveFailsForDisabledUrl() {
		ShortUrl shortUrl = new ShortUrl("gone1234", "https://example.com", NOW, null);
		shortUrl.disable();
		when(repository.findByShortCode("gone1234")).thenReturn(Optional.of(shortUrl));

		assertThatThrownBy(() -> service.resolveDestination("gone1234"))
				.isInstanceOf(ShortUrlGoneException.class)
				.hasMessageContaining("disabled");
	}

	@Test
	void metadataIsReturnedForExpiredUrl() {
		when(repository.findByShortCode("expired1"))
				.thenReturn(Optional.of(new ShortUrl("expired1", "https://example.com", NOW.minusSeconds(60),
						NOW.minusSeconds(1))));

		ShortUrlResponse response = service.getMetadata("expired1");

		assertThat(response.expired()).isTrue();
		assertThat(response.active()).isTrue();
	}

	@Test
	void metadataFailsForUnknownCode() {
		when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getMetadata("missing")).isInstanceOf(ShortUrlNotFoundException.class);
	}

	@Test
	void disableSoftDeletesTheShortUrl() {
		ShortUrl shortUrl = new ShortUrl("abc1234", "https://example.com", NOW, null);
		when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(shortUrl));
		when(repository.save(shortUrl)).thenReturn(shortUrl);

		service.disable("abc1234");

		assertThat(shortUrl.isActive()).isFalse();
		verify(repository).save(shortUrl);
	}

	@Test
	void disableFailsForUnknownCode() {
		when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.disable("missing")).isInstanceOf(ShortUrlNotFoundException.class);
	}
}
