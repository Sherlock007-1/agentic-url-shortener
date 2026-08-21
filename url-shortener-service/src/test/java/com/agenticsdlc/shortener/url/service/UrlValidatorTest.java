package com.agenticsdlc.shortener.url.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agenticsdlc.shortener.url.config.ShortenerProperties;
import com.agenticsdlc.shortener.url.exception.InvalidUrlException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlValidatorTest {

	private UrlValidator validator;

	@BeforeEach
	void setUp() {
		validator = new UrlValidator(new ShortenerProperties("http://localhost:8081", 7, 2048));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"https://example.com",
			"http://example.com/path?query=1#fragment",
			"https://sub.example.co.uk:8443/a/b"
	})
	void acceptsHttpAndHttpsUrls(String url) {
		assertThat(validator.validate(url)).isEqualTo(url);
	}

	@Test
	void trimsSurroundingWhitespace() {
		assertThat(validator.validate("  https://example.com  ")).isEqualTo("https://example.com");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"javascript:alert(1)",
			"file:///etc/passwd",
			"data:text/html;base64,PHNjcmlwdD4="
	})
	void rejectsUnsafeSchemes(String url) {
		assertThatThrownBy(() -> validator.validate(url)).isInstanceOf(InvalidUrlException.class);
	}

	@Test
	void rejectsRelativeUrl() {
		assertThatThrownBy(() -> validator.validate("/just/a/path"))
				.isInstanceOf(InvalidUrlException.class)
				.hasMessageContaining("absolute");
	}

	@Test
	void rejectsUrlWithoutHost() {
		assertThatThrownBy(() -> validator.validate("http:///no-host"))
				.isInstanceOf(InvalidUrlException.class)
				.hasMessageContaining("host");
	}

	@Test
	void rejectsMalformedUri() {
		assertThatThrownBy(() -> validator.validate("http://exa mple.com"))
				.isInstanceOf(InvalidUrlException.class)
				.hasMessageContaining("valid URI");
	}

	@Test
	void rejectsBlankUrl() {
		assertThatThrownBy(() -> validator.validate("   ")).isInstanceOf(InvalidUrlException.class);
	}

	@Test
	void rejectsTooLongUrl() {
		String longUrl = "https://example.com/" + "a".repeat(2100);
		assertThatThrownBy(() -> validator.validate(longUrl))
				.isInstanceOf(InvalidUrlException.class)
				.hasMessageContaining("2048");
	}
}
