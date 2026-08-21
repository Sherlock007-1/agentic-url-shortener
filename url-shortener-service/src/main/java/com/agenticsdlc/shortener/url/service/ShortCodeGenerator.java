package com.agenticsdlc.shortener.url.service;

import com.agenticsdlc.shortener.url.config.ShortenerProperties;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Generates random Base62 short codes.
 *
 * <p>The generator never checks the database: uniqueness is owned by the
 * {@code short_urls.short_code} unique constraint. {@link ShortUrlService} turns a
 * collision into a bounded regeneration, so this class stays a pure value producer.
 *
 * <p>{@link #generate()} is intentionally left overridable so tests can substitute
 * a deterministic sequence of codes and prove collision handling without relying on
 * randomness.
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
