package com.agenticsdlc.shortener.url.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agenticsdlc.shortener.url.config.ShortenerProperties;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShortCodeGeneratorTest {

	private final ShortCodeGenerator generator =
			new ShortCodeGenerator(new ShortenerProperties("http://localhost:8081", 7, 2048));

	@Test
	void generatesCodeOfConfiguredLengthUsingBase62Alphabet() {
		String code = generator.generate();
		assertThat(code).hasSize(7).matches("[A-Za-z0-9]+");
	}

	@Test
	void generatesDistinctCodesInPractice() {
		Set<String> codes = new HashSet<>();
		for (int i = 0; i < 1_000; i++) {
			codes.add(generator.generate());
		}
		assertThat(codes).hasSize(1_000);
	}
}
