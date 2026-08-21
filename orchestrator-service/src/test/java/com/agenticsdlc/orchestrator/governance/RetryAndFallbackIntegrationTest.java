package com.agenticsdlc.orchestrator.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.agenticsdlc.orchestrator.agent.Agent;
import com.agenticsdlc.orchestrator.agent.AgentFallback;
import com.agenticsdlc.orchestrator.agent.AgentResult;
import com.agenticsdlc.orchestrator.agent.RetryableAgentException;
import com.agenticsdlc.orchestrator.domain.AgentType;
import com.agenticsdlc.orchestrator.domain.AttemptKind;
import com.agenticsdlc.orchestrator.domain.AttemptOutcome;
import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.domain.TaskAttempt;
import com.agenticsdlc.orchestrator.domain.TaskStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowTask;
import com.agenticsdlc.orchestrator.engine.WorkflowQueryService;
import com.agenticsdlc.orchestrator.engine.WorkflowService;
import com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate;
import com.agenticsdlc.orchestrator.repository.AgentExecutionRepository;
import com.agenticsdlc.orchestrator.repository.TaskAttemptRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowTaskRepository;
import com.agenticsdlc.orchestrator.support.AbstractPostgresIntegrationTest;
import com.agenticsdlc.orchestrator.support.ScriptedAgent;
import com.agenticsdlc.orchestrator.support.ScriptedFallback;
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
 * Bounded agent retries and the approved fallback, proven with a scripted agent.
 *
 * <p>The script decides per attempt what happens, so the behaviour is fully
 * deterministic - no sleeps, no timing assumptions.
 */
@SpringBootTest
@TestPropertySource(properties = { "spring.main.allow-bean-definition-overriding=true",
		"orchestrator.governance.max-task-attempts=3" })
class RetryAndFallbackIntegrationTest extends AbstractPostgresIntegrationTest {

	@TestConfiguration
	static class ScriptedImplementation {

		@Bean
		Agent implementationAgent() {
			return new ScriptedAgent(AgentType.IMPLEMENTATION);
		}

		@Bean
		AgentFallback implementationFallback() {
			return new ScriptedFallback(AgentType.IMPLEMENTATION);
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
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private Agent implementationAgent;

	@Autowired
	private AgentFallback implementationFallback;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private ScriptedAgent agent;
	private ScriptedFallback fallback;

	@BeforeEach
	void resetState() {
		TestDatabase.clean(jdbcTemplate);
		agent = (ScriptedAgent) implementationAgent;
		fallback = (ScriptedFallback) implementationFallback;
	}

	@Test
	void aRetryableFailureIsRetriedWithinTheBoundAndTheWorkflowContinues() {
		UUID workflowId = createWorkflow();
		agent.scriptFor(workflowId, retryable("temporary model outage"), retryable("temporary model outage"),
				ScriptedAgent.SUCCESS);

		start(workflowId);
		awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.COMPLETED, Duration.ofSeconds(30));

		WorkflowTask implementation = implementationTask(workflowId);
		assertThat(implementation.getStatus()).isEqualTo(TaskStatus.COMPLETED);
		assertThat(implementation.getAttemptCount()).isEqualTo(3);

		List<TaskAttempt> attempts = attemptRepository.findByTaskIdOrderByAttemptNoAsc(implementation.getId());
		assertThat(attempts).hasSize(3);
		assertThat(attempts).allSatisfy(attempt -> assertThat(attempt.getKind()).isEqualTo(AttemptKind.PRIMARY));
		assertThat(attempts.subList(0, 2)).allSatisfy(attempt -> {
			assertThat(attempt.getOutcome()).isEqualTo(AttemptOutcome.FAILED);
			assertThat(attempt.isRetryable()).isTrue();
			assertThat(attempt.getErrorMessage()).contains("temporary model outage");
		});
		assertThat(attempts.get(2).getOutcome()).isEqualTo(AttemptOutcome.SUCCEEDED);

		List<AuditEventType> audit = auditTypes(workflowId);
		assertThat(audit.stream().filter(type -> type == AuditEventType.TASK_RETRY_SCHEDULED).count()).isEqualTo(2);
		assertThat(audit).contains(AuditEventType.TASK_ATTEMPT_FAILED, AuditEventType.TASK_RECOVERED,
				AuditEventType.TASK_COMPLETED);
		// A recovered task leaves exactly one successful agent execution behind.
		assertThat(agentExecutionRepository.findByTaskId(implementation.getId())).hasSize(1);
	}

	@Test
	void anExhaustedPrimaryIsRecoveredByTheApprovedFallbackAsAnExplicitlyDegradedResult() {
		UUID workflowId = createWorkflow();
		agent.scriptFor(workflowId, retryable("outage"), retryable("outage"), retryable("outage"));
		fallback.scriptFor(workflowId, context -> AgentResult.of("FALLBACK:" + context.taskKey(), "fallback result"));

		start(workflowId);
		awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.COMPLETED, Duration.ofSeconds(30));

		WorkflowTask implementation = implementationTask(workflowId);
		assertThat(implementation.getStatus()).isEqualTo(TaskStatus.COMPLETED);
		// 3 bounded primary attempts + exactly one fallback attempt.
		assertThat(implementation.getAttemptCount()).isEqualTo(4);
		assertThat(fallback.invocationsFor(workflowId)).isEqualTo(1);
		assertThat(implementation.getOutputContext()).startsWith("[FALLBACK/DEGRADED]");

		assertThat(attemptRepository.findByTaskIdOrderByAttemptNoAsc(implementation.getId()))
				.filteredOn(attempt -> attempt.getKind() == AttemptKind.FALLBACK)
				.singleElement()
				.satisfies(attempt -> assertThat(attempt.getOutcome()).isEqualTo(AttemptOutcome.SUCCEEDED));
		assertThat(auditTypes(workflowId)).contains(AuditEventType.FALLBACK_INVOKED,
				AuditEventType.FALLBACK_SUCCEEDED);
		assertThat(queryService.decisions(workflowId))
				.anySatisfy(decision -> {
					assertThat(decision.getDecisionType()).isEqualTo("FALLBACK");
					assertThat(decision.getRationale()).contains("not equivalent to a successful primary");
				});
	}

	@Test
	void aFailingFallbackSafeStopsTheWorkflow() {
		UUID workflowId = createWorkflow();
		agent.scriptFor(workflowId, retryable("outage"), retryable("outage"), retryable("outage"));
		fallback.scriptFor(workflowId, context -> {
			throw new IllegalStateException("fallback unavailable");
		});

		start(workflowId);
		WorkflowRun run = awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.SAFE_STOPPED,
				Duration.ofSeconds(30));

		assertThat(run.getSafeStopReason()).contains("approved fallback also failed")
				.contains("fallback unavailable");
		assertThat(implementationTask(workflowId).getStatus()).isEqualTo(TaskStatus.FAILED);
		assertThat(auditTypes(workflowId)).contains(AuditEventType.FALLBACK_INVOKED, AuditEventType.FALLBACK_FAILED,
				AuditEventType.WORKFLOW_SAFE_STOPPED);
		assertThat(taskRepository.findByWorkflowRunIdAndTaskKey(workflowId, SdlcWorkflowGraphTemplate.TESTS)
				.orElseThrow().getStatus()).isEqualTo(TaskStatus.PENDING);
	}

	@Test
	void aNonRetryableFailureIsExecutedExactlyOnceAndNeverFallsBack() {
		UUID workflowId = createWorkflow();
		agent.scriptFor(workflowId, context -> {
			throw new IllegalStateException("deterministic defect");
		});

		start(workflowId);
		WorkflowRun run = awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.FAILED, Duration.ofSeconds(30));

		WorkflowTask implementation = implementationTask(workflowId);
		assertThat(implementation.getAttemptCount()).isEqualTo(1);
		assertThat(agent.invocationsFor(workflowId)).isEqualTo(1);
		assertThat(fallback.invocationsFor(workflowId)).isZero();
		assertThat(run.getErrorMessage()).contains("deterministic defect");
		assertThat(attemptRepository.findByTaskIdOrderByAttemptNoAsc(implementation.getId())).singleElement()
				.satisfies(attempt -> assertThat(attempt.isRetryable()).isFalse());
		assertThat(auditTypes(workflowId)).doesNotContain(AuditEventType.TASK_RETRY_SCHEDULED,
				AuditEventType.FALLBACK_INVOKED);
	}

	private ScriptedAgent.Step retryable(String message) {
		return context -> {
			throw new RetryableAgentException(message);
		};
	}

	private UUID createWorkflow() {
		return workflowService.createWorkflow("Add click analytics for shortened URLs.").getId();
	}

	private void start(UUID workflowId) {
		workflowService.start(workflowId);
	}

	private WorkflowTask implementationTask(UUID workflowId) {
		return taskRepository.findByWorkflowRunIdAndTaskKey(workflowId, SdlcWorkflowGraphTemplate.IMPLEMENTATION)
				.orElseThrow();
	}

	private List<AuditEventType> auditTypes(UUID workflowId) {
		return queryService.auditTrail(workflowId).stream().map(event -> event.getEventType()).toList();
	}
}
