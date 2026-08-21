package com.agenticsdlc.orchestrator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.agenticsdlc.orchestrator.domain.AgentExecution;
import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.domain.Decision;
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
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runs the whole graph end to end with the deterministic agents and asserts the
 * resulting persisted state: statuses, ordering, cross-stage context, decisions
 * and audit trail.
 */
@SpringBootTest
class WorkflowExecutionIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String REQUIREMENT = "Add click analytics for shortened URLs.";

	@Autowired
	private WorkflowService workflowService;

	@Autowired
	private WorkflowQueryService queryService;

	@Autowired
	private ContextSerializer contextSerializer;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Autowired
	private WorkflowTaskRepository taskRepository;

	@Autowired
	private AgentExecutionRepository agentExecutionRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private UUID workflowId;
	private Map<String, WorkflowTask> tasksByKey;

	@BeforeEach
	void runWorkflow() {
		TestDatabase.clean(jdbcTemplate);
		workflowId = workflowService.createWorkflow(REQUIREMENT).getId();
		workflowService.start(workflowId);
		awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.COMPLETED, Duration.ofSeconds(30));
		tasksByKey = taskRepository.findByWorkflowRunIdOrderBySequenceNoAsc(workflowId).stream()
				.collect(Collectors.toMap(WorkflowTask::getTaskKey, Function.identity()));
	}

	@Test
	void everyTaskCompletesAndTheWorkflowIsMarkedCompleted() {
		WorkflowRun run = workflowRunRepository.findById(workflowId).orElseThrow();

		assertThat(run.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
		assertThat(run.getStartedAt()).isNotNull();
		assertThat(run.getCompletedAt()).isNotNull();
		assertThat(tasksByKey).hasSize(9);
		assertThat(tasksByKey.values()).allSatisfy(task -> {
			assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
			assertThat(task.getOutputContext()).isNotBlank();
		});
	}

	@Test
	void noTaskStartsBeforeItsPredecessorsCompleted() {
		Map<String, List<String>> dependencies = queryService
				.dependencyKeys(List.copyOf(tasksByKey.values()));

		dependencies.forEach((taskKey, predecessors) -> predecessors.forEach(predecessorKey -> {
			WorkflowTask task = tasksByKey.get(taskKey);
			WorkflowTask predecessor = tasksByKey.get(predecessorKey);
			assertThat(task.getStartedAt())
					.as("%s started after %s completed", taskKey, predecessorKey)
					.isAfterOrEqualTo(predecessor.getCompletedAt());
		}));
	}

	@Test
	void downstreamTasksReceiveTheOutputOfTheirPredecessors() {
		Map<String, String> codebaseInput = contextSerializer
				.read(tasksByKey.get(SdlcWorkflowGraphTemplate.CODEBASE_ANALYSIS).getInputContext());
		assertThat(codebaseInput).containsKey(SdlcWorkflowGraphTemplate.REQUIREMENT_ANALYSIS);
		assertThat(codebaseInput.get(SdlcWorkflowGraphTemplate.REQUIREMENT_ANALYSIS))
				.isEqualTo(tasksByKey.get(SdlcWorkflowGraphTemplate.REQUIREMENT_ANALYSIS).getOutputContext());

		// The requirement itself travels down the chain through the agent context.
		assertThat(tasksByKey.get(SdlcWorkflowGraphTemplate.PLANNING).getOutputContext()).contains(REQUIREMENT);
	}

	@Test
	void theJoinTaskSeesAllThreeParallelBranches() {
		Map<String, String> validationInput = contextSerializer
				.read(tasksByKey.get(SdlcWorkflowGraphTemplate.VALIDATION).getInputContext());

		assertThat(validationInput).containsOnlyKeys(SdlcWorkflowGraphTemplate.TESTS,
				SdlcWorkflowGraphTemplate.SECURITY, SdlcWorkflowGraphTemplate.DOCUMENTATION);
		assertThat(validationInput.values()).allSatisfy(value -> assertThat(value).isNotBlank());
	}

	@Test
	void planningAndArchitectureDecisionsArePersistedWithLineage() {
		List<Decision> decisions = queryService.decisions(workflowId);

		assertThat(decisions).extracting(Decision::getDecisionType).containsExactly("PLANNING", "ARCHITECTURE");
		assertThat(decisions).allSatisfy(decision -> {
			assertThat(decision.getWorkflowRunId()).isEqualTo(workflowId);
			assertThat(decision.getTaskId()).isNotNull();
			assertThat(decision.getRationale()).isNotBlank();
			assertThat(decision.getCreatedAt()).isNotNull();
		});
		assertThat(decisions.get(0).getTaskId()).isEqualTo(tasksByKey.get(SdlcWorkflowGraphTemplate.PLANNING).getId());
	}

	@Test
	void everyTaskHasAnAuditableAgentExecution() {
		List<AgentExecution> executions = agentExecutionRepository.findByWorkflowRunIdOrderByStartedAtAsc(workflowId);

		assertThat(executions).hasSize(9);
		assertThat(executions).allSatisfy(execution -> {
			assertThat(execution.getStatus()).isEqualTo(TaskStatus.COMPLETED);
			assertThat(execution.getOutput()).isNotBlank();
			assertThat(execution.getCompletedAt()).isNotNull();
		});
	}

	@Test
	void auditTrailCoversTheWholeLifecycle() {
		List<AuditEventType> types = queryService.auditTrail(workflowId).stream()
				.map(event -> event.getEventType())
				.toList();

		assertThat(types).startsWith(AuditEventType.WORKFLOW_CREATED, AuditEventType.GRAPH_CREATED,
				AuditEventType.WORKFLOW_STARTED);
		assertThat(types).endsWith(AuditEventType.WORKFLOW_COMPLETED);
		assertThat(types).contains(AuditEventType.TASK_READY, AuditEventType.TASK_STARTED,
				AuditEventType.TASK_COMPLETED, AuditEventType.DECISION_RECORDED);
		assertThat(types.stream().filter(type -> type == AuditEventType.TASK_COMPLETED).count()).isEqualTo(9);
	}

	@Test
	void stateSurvivesReloadFromTheDatabase() {
		// Nothing is cached in the engine: a fresh read returns the same terminal state.
		assertThat(workflowRunRepository.findById(workflowId).orElseThrow().getStatus())
				.isEqualTo(WorkflowStatus.COMPLETED);
		assertThat(taskRepository.findByWorkflowRunIdAndTaskKey(workflowId, SdlcWorkflowGraphTemplate.VALIDATION)
				.orElseThrow().getOutputContext()).contains("VALIDATION");
	}
}
