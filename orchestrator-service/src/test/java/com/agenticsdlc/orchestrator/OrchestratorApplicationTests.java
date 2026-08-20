package com.agenticsdlc.orchestrator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that the orchestrator application context bootstraps against a real
 * PostgreSQL instance (including Flyway migrations).
 *
 * <p>The test is skipped automatically when no Docker environment is available.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OrchestratorApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Test
	void contextLoads() {
		// Fails if the Spring context, datasource or Flyway migrations cannot start.
	}
}
