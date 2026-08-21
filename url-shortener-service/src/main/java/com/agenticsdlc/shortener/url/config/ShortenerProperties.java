package com.agenticsdlc.shortener.url.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for short link generation and presentation.
 *
 * @param baseUrl      public base URL used to build fully qualified short links
 * @param codeLength   number of characters in a generated short code
 * @param maxUrlLength maximum accepted destination URL length
 */
@ConfigurationProperties(prefix = "shortener")
public record ShortenerProperties(String baseUrl, int codeLength, int maxUrlLength) {

	public ShortenerProperties {
		baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "http://localhost:8081" : stripTrailingSlash(baseUrl);
		codeLength = codeLength <= 0 ? 7 : codeLength;
		maxUrlLength = maxUrlLength <= 0 ? 2048 : maxUrlLength;
	}

	private static String stripTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
