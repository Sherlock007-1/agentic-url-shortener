package com.agenticsdlc.orchestrator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.agenticsdlc.orchestrator.agent.Agent;
import com.agenticsdlc.orchestrator.agent.AgentContext;
import com.agenticsdlc.orchestrator.agent.AgentResult;
import com.agenticsdlc.orchestrator.domain.AgentType;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowTaskRepository;
import com.agenticsdlc.orchestrator.support.AbstractPostgresIntegrationTest;
import com.agenticsdlc.orchestrator.support.TestDatabase;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Proves that the Tests, Security and Documentation branches really execute
 * concurrently.
 *
 * <p>Each of the three agents waits on a shared {@link CyclicBarrier} of three
 * parties. The barrier can only trip if all three tasks are running at the same
 * moment; otherwise the agents time out, their tasks fail and the workflow ends
 * FAILED. The assertion is therefore on persisted state, not on measured timing.
 */
@SpringBootTest
@TestPropertySource(properties = {
		"spring.main.allow-bean-definition-overriding=true",
		"orchestrator.core-pool-size=4",
		"orchestrator.max-pool-size=4"
})
class ParallelBranchExecutionIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final CyclicBarrier BARRIER = new CyclicBarrier(3);

	@TestConfiguration
	static class BarrierAgents {

		@Bean
		Agent testAgent() {
			return new BarrierAgent(AgentType.TEST);
		}

		@Bean
		Agent securityRiskAgent() {
			return new BarrierAgent(AgentType.SECURITY_RISK);
		}

		@Bean
		Agent documentationAgent() {
			return new BarrierAgent(AgentType.DOCUMENTATION);
		}
	}

	/** Completes only if the two sibling branches are running concurrently. */
	record BarrierAgent(AgentType agentType) implements Agent {

		@Override
		public AgentType type() {
			return agentType;
		}

		@Override
		public AgentResult execute(AgentContext context) {
			try {
				BARRIER.await(20, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while waiting for sibling branches", ex);
			}
			catch (TimeoutException | BrokenBarrierException ex) {
				throw new IllegalStateException(
						"Branch " + agentType + " was not executed in parallel with its siblings", ex);
			}
			return AgentResult.of("parallel output of " + agentType, "ran in parallel");
		}
	}

	@Autowired
	private WorkflowService workflowService;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Autowired
	private WorkflowTaskRepository taskRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		TestDatabase.clean(jdbcTemplate);
		BARRIER.reset();
	}

	@Test
	void independentBranchesRunConcurrentlyAndTheJoinWaitsForAllOfThem() {
		UUID workflowId = workflowService.createWorkflow("Add click analytics for shortened URLs.").getId();

		workflowService.start(workflowId);

		// COMPLETED is only reachable if the three-party barrier tripped.
		awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.COMPLETED, Duration.ofSeconds(60));

		var validation = taskRepository
				.findByWorkflowRunIdAndTaskKey(workflowId, SdlcWorkflowGraphTemplate.VALIDATION).orElseThrow();
		var tests = taskRepository.findByWorkflowRunIdAndTaskKey(workflowId, SdlcWorkflowGraphTemplate.TESTS)
				.orElseThrow();
		var security = taskRepository.findByWorkflowRunIdAndTaskKey(workflowId, SdlcWorkflowGraphTemplate.SECURITY)
				.orElseThrow();
		var documentation = taskRepository
				.findByWorkflowRunIdAndTaskKey(workflowId, SdlcWorkflowGraphTemplate.DOCUMENTATION).orElseThrow();

		// Overlapping execution windows, plus a join that starts after all of them.
		assertThat(tests.getStartedAt()).isBefore(security.getCompletedAt());
		assertThat(security.getStartedAt()).isBefore(documentation.getCompletedAt());
		assertThat(validation.getStartedAt())
				.isAfterOrEqualTo(tests.getCompletedAt())
				.isAfterOrEqualTo(security.getCompletedAt())
				.isAfterOrEqualTo(documentation.getCompletedAt());
	}
}
