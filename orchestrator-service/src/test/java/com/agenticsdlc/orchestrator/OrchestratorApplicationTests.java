package com.agenticsdlc.orchestrator;

import com.agenticsdlc.orchestrator.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that the orchestrator application context bootstraps against a real
 * PostgreSQL instance (including Flyway migrations).
 */
@SpringBootTest
class OrchestratorApplicationTests extends AbstractPostgresIntegrationTest {

	@Test
	void contextLoads() {
		// Fails if the Spring context, datasource or Flyway migrations cannot start.
	}
}