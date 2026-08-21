package com.agenticsdlc.orchestrator.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agenticsdlc.orchestrator.agent.Agent;
import com.agenticsdlc.orchestrator.agent.RetryableAgentException;
import com.agenticsdlc.orchestrator.domain.AgentType;
import com.agenticsdlc.orchestrator.domain.TaskAttempt;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.domain.WorkspaceSnapshot;
import com.agenticsdlc.orchestrator.engine.WorkflowService;
import com.agenticsdlc.orchestrator.governance.WorkspaceSnapshotService;
import com.agenticsdlc.orchestrator.repository.TaskAttemptRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import com.agenticsdlc.orchestrator.support.AbstractPostgresIntegrationTest;
import com.agenticsdlc.orchestrator.support.ScriptedAgent;
import com.agenticsdlc.orchestrator.support.TestDatabase;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Metrics are recomputed from persisted rows only.
 *
 * <p>Every expectation below is derived from the repositories in the test itself,
 * so a wrong formula fails instead of matching a hard-coded number.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "spring.main.allow-bean-definition-overriding=true",
		"orchestrator.governance.max-task-attempts=3" })
class MetricsIntegrationTest extends AbstractPostgresIntegrationTest {

	@TestConfiguration
	static class ScriptedImplementation {

		@Bean
		Agent implementationAgent() {
			return new ScriptedAgent(AgentType.IMPLEMENTATION);
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private WorkflowService workflowService;

	@Autowired
	private MetricsService metricsService;

	@Autowired
	private WorkspaceSnapshotService snapshotService;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Autowired
	private TaskAttemptRepository attemptRepository;

	@Autowired
	private Agent implementationAgent;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private ScriptedAgent agent;

	@BeforeEach
	void cleanDatabase() {
		TestDatabase.clean(jdbcTemplate);
		agent = (ScriptedAgent) implementationAgent;
	}

	@Test
	void anEmptySystemReportsZerosAndNullsInsteadOfInventedNumbers() {
		MetricsResponse metrics = metricsService.metrics();

		assertThat(metrics.workflowsTotal()).isZero();
		assertThat(metrics.workflowSuccessRate()).isNull();
		assertThat(metrics.agentRetriesPerWorkflow()).isNull();
		assertThat(metrics.meanTimeToRecoverySeconds()).isNull();
		assertThat(metrics.recoverySamples()).isZero();
		assertThat(metrics.meanWorkflowLatencySeconds()).isNull();
		assertThat(metrics.maxWorkflowLatencySeconds()).isNull();
		assertThat(metrics.rollbackCount()).isZero();
	}

	@Test
	void metricsAreDerivedFromThePersistedRunsAttemptsAndSnapshots() throws Exception {
		// One clean run, one run that recovers after two retryable failures.
		UUID clean = runToCompletion(false);
		UUID recovered = runToCompletion(true);

		WorkspaceSnapshot snapshot = snapshotService.snapshot(clean, null, "metrics-demo");
		snapshotService.rollback(clean, snapshot.getId());

		MetricsResponse metrics = metricsService.metrics();

		List<WorkflowRun> runs = workflowRunRepository.findAll();
		long completed = runs.stream().filter(run -> run.getStatus() == WorkflowStatus.COMPLETED).count();
		List<TaskAttempt> attempts = attemptRepository.findAll();
		long expectedRetries = attempts.stream().filter(attempt -> attempt.getAttemptNo() > 1).count();

		assertThat(metrics.workflowsTotal()).isEqualTo(runs.size());
		assertThat(metrics.workflowsCompleted()).isEqualTo(completed);
		assertThat(metrics.workflowsFailed()).isZero();
		assertThat(metrics.workflowsSafeStopped()).isZero();
		assertThat(metrics.workflowSuccessRate()).isEqualTo(1.0d);

		assertThat(expectedRetries).isEqualTo(2);
		assertThat(metrics.agentRetryCount()).isEqualTo(expectedRetries);
		assertThat(metrics.taskAttemptCount()).isEqualTo(attempts.size());
		assertThat(metrics.agentRetriesPerWorkflow()).isEqualTo((double) expectedRetries / runs.size());

		assertThat(metrics.snapshotCount()).isEqualTo(1);
		assertThat(metrics.rollbackCount()).isEqualTo(1);
		assertThat(metrics.rollbacksPerWorkflow()).isEqualTo(1.0d / runs.size());

		// Exactly one task failed and later succeeded, so there is exactly one sample.
		assertThat(metrics.recoverySamples()).isEqualTo(1);
		assertThat(metrics.meanTimeToRecoverySeconds()).isNotNull().isGreaterThanOrEqualTo(0.0d);

		double expectedMeanLatency = runs.stream()
				.filter(run -> run.getStatus() == WorkflowStatus.COMPLETED)
				.mapToDouble(run -> Duration.between(run.getStartedAt(), run.getCompletedAt()).toNanos()
						/ 1_000_000_000.0d)
				.average()
				.orElseThrow();
		assertThat(metrics.meanWorkflowLatencySeconds()).isCloseTo(expectedMeanLatency,
				org.assertj.core.data.Offset.offset(0.000001d));
		assertThat(metrics.maxWorkflowLatencySeconds()).isGreaterThanOrEqualTo(metrics.meanWorkflowLatencySeconds());

		assertThat(recovered).isNotEqualTo(clean);
	}

	@Test
	void theMetricsEndpointExposesTheSameDerivedValues() throws Exception {
		runToCompletion(false);

		MetricsResponse metrics = metricsService.metrics();

		mockMvc.perform(get("/api/metrics"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.workflowsTotal").value((int) metrics.workflowsTotal()))
				.andExpect(jsonPath("$.workflowsCompleted").value((int) metrics.workflowsCompleted()))
				.andExpect(jsonPath("$.workflowSuccessRate").value(metrics.workflowSuccessRate()))
				.andExpect(jsonPath("$.agentRetryCount").value((int) metrics.agentRetryCount()))
				.andExpect(jsonPath("$.rollbackCount").value((int) metrics.rollbackCount()));
	}

	private ScriptedAgent.Step retryable() {
		return context -> {
			throw new RetryableAgentException("transient failure");
		};
	}

	private UUID runToCompletion(boolean withRetries) {
		UUID workflowId = workflowService.createWorkflow("Add click analytics for shortened URLs.").getId();
		if (withRetries) {
			agent.scriptFor(workflowId, retryable(), retryable(), ScriptedAgent.SUCCESS);
		}
		workflowService.start(workflowId);
		awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.COMPLETED, Duration.ofSeconds(30));
		return workflowId;
	}
}
