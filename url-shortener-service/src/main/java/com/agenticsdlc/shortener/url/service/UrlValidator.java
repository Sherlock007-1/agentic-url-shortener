package com.agenticsdlc.shortener.url.service;

import com.agenticsdlc.shortener.url.config.ShortenerProperties;
import com.agenticsdlc.shortener.url.exception.InvalidUrlException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Validates destination URLs before they are shortened.
 *
 * <p>The rules are intentionally narrow and explainable:
 * <ul>
 *   <li>the value must parse as an absolute URI;</li>
 *   <li>only the {@code http} and {@code https} schemes are accepted, which also
 *       rejects unsafe schemes such as {@code javascript:}, {@code file:} and {@code data:};</li>
 *   <li>a host must be present;</li>
 *   <li>the URL must not exceed a configured maximum length.</li>
 * </ul>
 *
 * <p>This is <em>not</em> SSRF or open-redirect protection: no DNS resolution,
 * network-range filtering or destination reputation checks are performed.
 */
@Component
public class UrlValidator {

	private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

	private final int maxUrlLength;

	public UrlValidator(ShortenerProperties properties) {
		this.maxUrlLength = properties.maxUrlLength();
	}

	/**
	 * @return the trimmed, validated URL
	 * @throws InvalidUrlException if the URL is not acceptable
	 */
	public String validate(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			throw new InvalidUrlException("url must not be blank");
		}
		String url = rawUrl.trim();
		if (url.length() > maxUrlLength) {
			throw new InvalidUrlException("url must not exceed " + maxUrlLength + " characters");
		}

		URI uri;
		try {
			uri = new URI(url);
		}
		catch (URISyntaxException ex) {
			throw new InvalidUrlException("url is not a valid URI: " + ex.getReason());
		}

		if (!uri.isAbsolute() || uri.getScheme() == null) {
			throw new InvalidUrlException("url must be absolute and include a scheme");
		}

		String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
		if (!ALLOWED_SCHEMES.contains(scheme)) {
			throw new InvalidUrlException("url scheme '" + scheme + "' is not allowed; only http and https are supported");
		}

		if (uri.getHost() == null || uri.getHost().isBlank()) {
			throw new InvalidUrlException("url must contain a host");
		}

		return url;
	}
}
