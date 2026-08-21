package com.agenticsdlc.orchestrator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.agenticsdlc.orchestrator.agent.Agent;
import com.agenticsdlc.orchestrator.agent.AgentContext;
import com.agenticsdlc.orchestrator.agent.AgentResult;
import com.agenticsdlc.orchestrator.domain.AgentType;
import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.domain.TaskStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowTask;
import com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate;
import com.agenticsdlc.orchestrator.repository.AgentExecutionRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowTaskRepository;
import com.agenticsdlc.orchestrator.support.AbstractPostgresIntegrationTest;
import com.agenticsdlc.orchestrator.support.TestDatabase;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * A failing agent must leave a persisted, explainable failure: the task and the
 * workflow are FAILED and the downstream tasks never start.
 *
 * <p>Retry, fallback and rollback are intentionally not part of this branch.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class WorkflowFailureIntegrationTest extends AbstractPostgresIntegrationTest {

	@TestConfiguration
	static class FailingImplementation {

		@Bean
		Agent implementationAgent() {
			return new Agent() {
				@Override
				public AgentType type() {
					return AgentType.IMPLEMENTATION;
				}

				@Override
				public AgentResult execute(AgentContext context) {
					throw new IllegalStateException("implementation agent exploded");
				}
			};
		}
	}

	@Autowired
	private WorkflowService workflowService;

	@Autowired
	private WorkflowQueryService queryService;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Autowired
	private WorkflowTaskRepository taskRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		TestDatabase.clean(jdbcTemplate);
	}

	@Test
	void aFailingTaskFailsTheWorkflowAndBlocksDownstreamTasks() {
		UUID workflowId = workflowService.createWorkflow("Add click analytics for shortened URLs.").getId();

		workflowService.start(workflowId);
		WorkflowRun run = awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.FAILED, Duration.ofSeconds(30));

		assertThat(run.getErrorMessage()).contains(SdlcWorkflowGraphTemplate.IMPLEMENTATION)
				.contains("implementation agent exploded");

		WorkflowTask implementation = taskRepository
				.findByWorkflowRunIdAndTaskKey(workflowId, SdlcWorkflowGraphTemplate.IMPLEMENTATION).orElseThrow();
		assertThat(implementation.getStatus()).isEqualTo(TaskStatus.FAILED);
		assertThat(implementation.getErrorMessage()).contains("exploded");

		for (String downstream : List.of(SdlcWorkflowGraphTemplate.TESTS, SdlcWorkflowGraphTemplate.SECURITY,
				SdlcWorkflowGraphTemplate.DOCUMENTATION, SdlcWorkflowGraphTemplate.VALIDATION)) {
			assertThat(taskRepository.findByWorkflowRunIdAndTaskKey(workflowId, downstream).orElseThrow().getStatus())
					.as("%s must not run after a failed predecessor", downstream)
					.isEqualTo(TaskStatus.PENDING);
		}
	}

	@Test
	void theFailureIsAuditedAndTheAgentExecutionIsRecorded() {
		UUID workflowId = workflowService.createWorkflow("Add click analytics for shortened URLs.").getId();

		workflowService.start(workflowId);
		awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.FAILED, Duration.ofSeconds(30));

		assertThat(queryService.auditTrail(workflowId)).extracting(event -> event.getEventType())
				.contains(AuditEventType.TASK_FAILED, AuditEventType.WORKFLOW_FAILED);

		WorkflowTask implementation = taskRepository
				.findByWorkflowRunIdAndTaskKey(workflowId, SdlcWorkflowGraphTemplate.IMPLEMENTATION).orElseThrow();
		assertThat(agentExecutionRepository.findByTaskId(implementation.getId())).singleElement()
				.satisfies(execution -> {
					assertThat(execution.getStatus()).isEqualTo(TaskStatus.FAILED);
					assertThat(execution.getErrorMessage()).contains("exploded");
				});
	}
}
