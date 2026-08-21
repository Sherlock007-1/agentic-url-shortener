package com.agenticsdlc.shortener.url.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Configuration for short link generation and presentation.
 *
 * @param baseUrl      public base URL used to build fully qualified short links
 * @param codeLength   number of characters in a generated short code
 * @param maxUrlLength maximum accepted destination URL length
 * @param maxCodeGenerationAttempts bounded short-code generation attempts before a
 *                                  create request fails with a conflict (default 3)
 */
@ConfigurationProperties(prefix = "shortener")
public record ShortenerProperties(String baseUrl, int codeLength, int maxUrlLength, int maxCodeGenerationAttempts) {

	// A second constructor exists for readability in tests, so the constructor used
	// for property binding has to be pointed out explicitly.
	@ConstructorBinding
	public ShortenerProperties {
		baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "http://localhost:8081" : stripTrailingSlash(baseUrl);
		codeLength = codeLength <= 0 ? 7 : codeLength;
		maxUrlLength = maxUrlLength <= 0 ? 2048 : maxUrlLength;
		maxCodeGenerationAttempts = maxCodeGenerationAttempts <= 0 ? 3 : maxCodeGenerationAttempts;
	}

	/** Convenience constructor keeping the generation attempts at their default. */
	public ShortenerProperties(String baseUrl, int codeLength, int maxUrlLength) {
		this(baseUrl, codeLength, maxUrlLength, 0);
	}

	private static String stripTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}