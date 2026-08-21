package com.agenticsdlc.orchestrator.support;

import static org.assertj.core.api.Assertions.fail;

import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import java.time.Duration;
import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for orchestrator tests that run against a real PostgreSQL instance
 * with the Flyway migrations applied.
 *
 * <p>Singleton-container pattern: one container for the whole test JVM. The
 * container is started unconditionally, so the tests fail rather than silently
 * skip when no Docker environment is available.
 */
public abstract class AbstractPostgresIntegrationTest {

	protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

	static {
		POSTGRES.start();
	}

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	/**
	 * Polls the database until the workflow reaches the expected status.
	 *
	 * <p>The assertion is on persisted state, not on timing: the timeout only
	 * bounds the test, it is never used to prove ordering or concurrency.
	 */
	protected WorkflowRun awaitStatus(WorkflowRunRepository repository, UUID workflowRunId, WorkflowStatus expected,
			Duration timeout) {
		long deadline = System.nanoTime() + timeout.toNanos();
		WorkflowStatus last = null;
		while (System.nanoTime() < deadline) {
			WorkflowRun run = repository.findById(workflowRunId).orElseThrow();
			last = run.getStatus();
			if (last == expected) {
				return run;
			}
			sleep();
		}
		fail("Workflow %s did not reach %s within %s (last status: %s)", workflowRunId, expected, timeout, last);
		return null;
	}

	private void sleep() {
		try {
			Thread.sleep(25);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}
}
