package com.agenticsdlc.shortener;

import com.agenticsdlc.shortener.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that the URL shortener application context bootstraps against a real
 * PostgreSQL instance (including Flyway migrations).
 */
@SpringBootTest
class UrlShortenerApplicationTests extends AbstractPostgresIntegrationTest {

	@Test
	void contextLoads() {
		// Fails if the Spring context, datasource or Flyway migrations cannot start.
	}
}