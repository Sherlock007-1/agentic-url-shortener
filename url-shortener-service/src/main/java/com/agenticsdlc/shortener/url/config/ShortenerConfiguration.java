package com.agenticsdlc.shortener.url.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShortenerConfiguration {

	/** A single injectable clock keeps expiration logic testable. */
	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
