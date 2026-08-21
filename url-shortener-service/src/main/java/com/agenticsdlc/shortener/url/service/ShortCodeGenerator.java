package com.agenticsdlc.shortener.url.service;

import com.agenticsdlc.shortener.url.config.ShortenerProperties;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Generates random Base62 short codes.
 *
 * <p>Uniqueness is currently enforced by the {@code short_urls.short_code} unique
 * constraint: on the rare event of a collision the insert fails. Collision-safe
 * retry behaviour is deliberately out of scope for this baseline.
 */
@Component
public class ShortCodeGenerator {

	private static final char[] ALPHABET =
			"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

	private final SecureRandom random = new SecureRandom();
	private final int codeLength;

	public ShortCodeGenerator(ShortenerProperties properties) {
		this.codeLength = properties.codeLength();
	}

	public String generate() {
		StringBuilder code = new StringBuilder(codeLength);
		for (int i = 0; i < codeLength; i++) {
			code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
		}
		return code.toString();
	}
}
