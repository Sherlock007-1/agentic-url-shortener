package com.agenticsdlc.orchestrator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import com.agenticsdlc.orchestrator.support.AbstractPostgresIntegrationTest;
import com.agenticsdlc.orchestrator.support.TestDatabase;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Proves the background poller alone can drive a workflow to completion.
 *
 * <p>The workflow is only transitioned to RUNNING in the database (no dispatch),
 * which is exactly the state an orchestrator instance would find after a restart.
 * This is the single test class that enables the scheduler, so no other cached
 * Spring context can interfere with the shared database.
 */
@SpringBootTest
@TestPropertySource(properties = {
		"orchestrator.scheduler-enabled=true",
		"orchestrator.poll-interval-ms=200"
})
class WorkflowSchedulerIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private WorkflowService workflowService;

	@Autowired
	private WorkflowTransitionService transitionService;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		TestDatabase.clean(jdbcTemplate);
	}

	@Test
	void pollerPicksUpRunningWorkflowsWithoutAnyInMemoryState() {
		UUID workflowId = workflowService.createWorkflow("Add click analytics for shortened URLs.").getId();

		// Only the persisted status changes; the engine is never invoked directly.
		transitionService.startWorkflow(workflowId);

		assertThat(workflowRunRepository.findById(workflowId).orElseThrow().getStatus())
				.isEqualTo(WorkflowStatus.RUNNING);
		awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.COMPLETED, Duration.ofSeconds(60));
	}
}
