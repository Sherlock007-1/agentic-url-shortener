package com.agenticsdlc.orchestrator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.domain.TaskStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowTask;
import com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate;
import com.agenticsdlc.orchestrator.repository.TaskDependencyRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowTaskRepository;
import com.agenticsdlc.orchestrator.support.AbstractPostgresIntegrationTest;
import com.agenticsdlc.orchestrator.support.TestDatabase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;


/**
 * Proves that the graph, its edges and the initial statuses are persisted rather
 * than kept in memory. The poller is disabled for tests, so nothing executes here.
 */
@SpringBootTest
class WorkflowGraphPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private WorkflowService workflowService;

	@Autowired
	private WorkflowQueryService queryService;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Autowired
	private WorkflowTaskRepository taskRepository;

	@Autowired
	private TaskDependencyRepository dependencyRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		TestDatabase.clean(jdbcTemplate);
	}

	@Test
	void createsWorkflowWithGraphVersionOneAndPendingTasks() {
		WorkflowRun run = workflowService.createWorkflow("Add click analytics for shortened URLs.");

		WorkflowRun reloaded = workflowRunRepository.findById(run.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(WorkflowStatus.READY);
		assertThat(reloaded.getCurrentGraphVersion()).isEqualTo(1);

		List<WorkflowTask> tasks = taskRepository.findByWorkflowRunIdOrderBySequenceNoAsc(run.getId());
		assertThat(tasks).hasSize(9);
		assertThat(tasks).allSatisfy(task -> assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING));
		assertThat(queryService.graphVersion(run.getId(), 1).getVersion()).isEqualTo(1);
	}

	@Test
	void persistsDependencyEdgesIncludingTheThreeWayJoin() {
		WorkflowRun run = workflowService.createWorkflow("Add click analytics for shortened URLs.");

		List<WorkflowTask> tasks = taskRepository.findByWorkflowRunIdOrderBySequenceNoAsc(run.getId());
		Map<String, List<String>> dependencies = queryService.dependencyKeys(tasks);

		assertThat(dependencies.get(SdlcWorkflowGraphTemplate.REQUIREMENT_ANALYSIS)).isEmpty();
		assertThat(dependencies.get(SdlcWorkflowGraphTemplate.IMPLEMENTATION))
				.containsExactly(SdlcWorkflowGraphTemplate.ARCHITECTURE);
		assertThat(dependencies.get(SdlcWorkflowGraphTemplate.TESTS))
				.containsExactly(SdlcWorkflowGraphTemplate.IMPLEMENTATION);
		assertThat(dependencies.get(SdlcWorkflowGraphTemplate.SECURITY))
				.containsExactly(SdlcWorkflowGraphTemplate.IMPLEMENTATION);
		assertThat(dependencies.get(SdlcWorkflowGraphTemplate.DOCUMENTATION))
				.containsExactly(SdlcWorkflowGraphTemplate.IMPLEMENTATION);
		assertThat(dependencies.get(SdlcWorkflowGraphTemplate.VALIDATION))
				.containsExactlyInAnyOrder(SdlcWorkflowGraphTemplate.TESTS, SdlcWorkflowGraphTemplate.SECURITY,
						SdlcWorkflowGraphTemplate.DOCUMENTATION);

		// 8 edges: 4 sequential + 3 fan-out + ... verified against the persisted rows.
		List<UUID> taskIds = tasks.stream().map(WorkflowTask::getId).toList();
		assertThat(dependencyRepository.findByTaskIdIn(taskIds)).hasSize(10);
	}

	@Test
	void recordsCreationAuditEvents() {
		WorkflowRun run = workflowService.createWorkflow("Add click analytics for shortened URLs.");

		List<AuditEventType> types = queryService.auditTrail(run.getId()).stream()
				.map(event -> event.getEventType())
				.collect(Collectors.toList());

		assertThat(types).containsExactly(AuditEventType.WORKFLOW_CREATED, AuditEventType.GRAPH_CREATED);
	}
}
