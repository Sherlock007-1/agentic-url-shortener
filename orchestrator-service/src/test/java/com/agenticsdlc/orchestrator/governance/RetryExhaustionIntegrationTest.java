package com.agenticsdlc.orchestrator.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.agenticsdlc.orchestrator.agent.Agent;
import com.agenticsdlc.orchestrator.agent.RetryableAgentException;
import com.agenticsdlc.orchestrator.domain.AgentType;
import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.domain.TaskStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowTask;
import com.agenticsdlc.orchestrator.engine.WorkflowQueryService;
import com.agenticsdlc.orchestrator.engine.WorkflowService;
import com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate;
import com.agenticsdlc.orchestrator.repository.TaskAttemptRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowTaskRepository;
import com.agenticsdlc.orchestrator.support.AbstractPostgresIntegrationTest;
import com.agenticsdlc.orchestrator.support.ScriptedAgent;
import com.agenticsdlc.orchestrator.support.TestDatabase;
import java.time.Duration;
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
 * Without an approved fallback an exhausted retry budget must lead to a controlled
 * stop - never to an endless retry loop and never to a fabricated result.
 */
@SpringBootTest
@TestPropertySource(properties = { "spring.main.allow-bean-definition-overriding=true",
		"orchestrator.governance.max-task-attempts=3" })
class RetryExhaustionIntegrationTest extends AbstractPostgresIntegrationTest {

	@TestConfiguration
	static class AlwaysFailingImplementation {

		@Bean
		Agent implementationAgent() {
			return new ScriptedAgent(AgentType.IMPLEMENTATION);
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
	private TaskAttemptRepository attemptRepository;

	@Autowired
	private Agent implementationAgent;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		TestDatabase.clean(jdbcTemplate);
	}

	@Test
	void anExhaustedRetryBudgetSafeStopsInsteadOfRetryingForever() {
		ScriptedAgent agent = (ScriptedAgent) implementationAgent;
		UUID workflowId = workflowService.createWorkflow("Add click analytics for shortened URLs.").getId();
		agent.scriptFor(workflowId, alwaysRetryable(), alwaysRetryable(), alwaysRetryable(), alwaysRetryable(),
				alwaysRetryable());

		workflowService.start(workflowId);
		WorkflowRun run = awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.SAFE_STOPPED,
				Duration.ofSeconds(30));

		assertThat(run.getSafeStopReason()).contains("exhausted 3 bounded attempts")
				.contains("no approved fallback");
		WorkflowTask implementation = taskRepository
				.findByWorkflowRunIdAndTaskKey(workflowId, SdlcWorkflowGraphTemplate.IMPLEMENTATION).orElseThrow();
		assertThat(implementation.getStatus()).isEqualTo(TaskStatus.FAILED);
		assertThat(implementation.getAttemptCount()).isEqualTo(3);
		assertThat(agent.invocationsFor(workflowId)).isEqualTo(3);
		assertThat(attemptRepository.findByTaskIdOrderByAttemptNoAsc(implementation.getId())).hasSize(3);

		assertThat(queryService.auditTrail(workflowId)).extracting(event -> event.getEventType())
				.contains(AuditEventType.TASK_ATTEMPT_FAILED, AuditEventType.TASK_RETRY_SCHEDULED,
						AuditEventType.WORKFLOW_SAFE_STOPPED);
	}

	@Test
	void aSafeStoppedWorkflowIsTerminalAndIsNotPickedUpAgain() {
		ScriptedAgent agent = (ScriptedAgent) implementationAgent;
		UUID workflowId = workflowService.createWorkflow("Add click analytics for shortened URLs.").getId();
		agent.scriptFor(workflowId, alwaysRetryable(), alwaysRetryable(), alwaysRetryable());

		workflowService.start(workflowId);
		awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.SAFE_STOPPED, Duration.ofSeconds(30));

		int attemptsAfterStop = agent.invocationsFor(workflowId);
		assertThat(workflowRunRepository.findByStatusIn(java.util.List.of(WorkflowStatus.RUNNING))).isEmpty();
		assertThat(agent.invocationsFor(workflowId)).isEqualTo(attemptsAfterStop).isEqualTo(3);
		assertThat(workflowRunRepository.findById(workflowId).orElseThrow().getStatus().isTerminal()).isTrue();
	}

	private ScriptedAgent.Step alwaysRetryable() {
		return context -> {
			throw new RetryableAgentException("model endpoint unavailable");
		};
	}
}
