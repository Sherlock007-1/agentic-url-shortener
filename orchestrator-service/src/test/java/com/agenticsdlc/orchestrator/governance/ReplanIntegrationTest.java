package com.agenticsdlc.orchestrator.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.domain.TaskStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowGraphVersion;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowTask;
import com.agenticsdlc.orchestrator.engine.WorkflowQueryService;
import com.agenticsdlc.orchestrator.engine.WorkflowService;
import com.agenticsdlc.orchestrator.repository.WorkflowReplanRepository;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Replanning creates graph version 2 and keeps version 1 intact.
 *
 * <p>The strongest assertion here is the negative one: nothing of version 1 is
 * modified or deleted, so the history of the first plan stays explainable.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReplanIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String ORIGINAL = "Add click analytics for shortened URLs.";
	private static final String CHANGED = "Add click analytics with per-country breakdown for shortened URLs.";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private WorkflowService workflowService;

	@Autowired
	private WorkflowQueryService queryService;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Autowired
	private WorkflowTaskRepository taskRepository;

	@Autowired
	private WorkflowReplanRepository replanRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private UUID workflowId;
	private List<WorkflowTask> version1TasksBeforeReplan;

	@BeforeEach
	void runVersionOne() {
		TestDatabase.clean(jdbcTemplate);
		workflowId = workflowService.createWorkflow(ORIGINAL).getId();
		workflowService.start(workflowId);
		awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.COMPLETED, Duration.ofSeconds(30));
		version1TasksBeforeReplan = queryService.tasks(workflowId, 1);
	}

	@Test
	void replanningCreatesVersionTwoAndPreservesVersionOne() throws Exception {
		mockMvc.perform(replanRequest())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fromGraphVersion").value(1))
				.andExpect(jsonPath("$.toGraphVersion").value(2))
				.andExpect(jsonPath("$.previousRequirement").value(ORIGINAL))
				.andExpect(jsonPath("$.newRequirement").value(CHANGED));

		WorkflowRun run = workflowRunRepository.findById(workflowId).orElseThrow();
		assertThat(run.getCurrentGraphVersion()).isEqualTo(2);
		assertThat(run.getStatus()).isEqualTo(WorkflowStatus.READY);

		List<WorkflowTask> version1 = queryService.tasks(workflowId, 1);
		assertThat(version1).hasSize(version1TasksBeforeReplan.size());
		assertThat(version1).extracting(WorkflowTask::getId)
				.containsExactlyElementsOf(version1TasksBeforeReplan.stream().map(WorkflowTask::getId).toList());
		assertThat(version1).allSatisfy(task -> {
			assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
			assertThat(task.getOutputContext()).isNotBlank();
		});

		List<WorkflowTask> version2 = queryService.tasks(workflowId, 2);
		assertThat(version2).hasSize(9);
		assertThat(version2).allSatisfy(task -> assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING));
		assertThat(version2).extracting(WorkflowTask::getId)
				.doesNotContainAnyElementsOf(version1.stream().map(WorkflowTask::getId).toList());
		assertThat(taskRepository.findByWorkflowRunIdOrderBySequenceNoAsc(workflowId)).hasSize(18);
	}

	@Test
	void bothGraphVersionsRemainQueryableThroughTheApi() throws Exception {
		mockMvc.perform(replanRequest()).andExpect(status().isOk());

		mockMvc.perform(get("/api/workflows/{id}/graph/versions", workflowId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].version").value(1))
				.andExpect(jsonPath("$[0].current").value(false))
				.andExpect(jsonPath("$[0].taskCount").value(9))
				.andExpect(jsonPath("$[1].version").value(2))
				.andExpect(jsonPath("$[1].current").value(true));

		mockMvc.perform(get("/api/workflows/{id}/graph", workflowId).param("version", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.tasks[?(@.taskKey=='validation')].status")
						.value(org.hamcrest.Matchers.contains("COMPLETED")));

		mockMvc.perform(get("/api/workflows/{id}/graph", workflowId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(2))
				.andExpect(jsonPath("$.tasks[?(@.taskKey=='validation')].status")
						.value(org.hamcrest.Matchers.contains("PENDING")));
	}

	@Test
	void theReplanReasonAndLineageArePersisted() throws Exception {
		mockMvc.perform(replanRequest()).andExpect(status().isOk());

		assertThat(replanRepository.findByWorkflowRunIdOrderByCreatedAtAsc(workflowId)).singleElement()
				.satisfies(replan -> {
					assertThat(replan.getReason()).contains("stakeholder feedback");
					assertThat(replan.getPreviousRequirement()).isEqualTo(ORIGINAL);
					assertThat(replan.getNewRequirement()).isEqualTo(CHANGED);
					assertThat(replan.getCreatedAt()).isNotNull();
				});
		assertThat(queryService.auditTrail(workflowId)).extracting(event -> event.getEventType())
				.contains(AuditEventType.REPLAN_STARTED, AuditEventType.GRAPH_CREATED,
						AuditEventType.REPLAN_COMPLETED);
		assertThat(queryService.decisions(workflowId))
				.anySatisfy(decision -> {
					assertThat(decision.getDecisionType()).isEqualTo("REPLAN");
					assertThat(decision.getRationale()).contains("remains persisted for lineage");
				});
		assertThat(queryService.graphVersions(workflowId)).extracting(WorkflowGraphVersion::getVersion)
				.containsExactly(1, 2);
	}

	@Test
	void versionTwoExecutesWhileVersionOneStaysUntouched() throws Exception {
		mockMvc.perform(replanRequest()).andExpect(status().isOk());

		workflowService.start(workflowId);
		awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.COMPLETED, Duration.ofSeconds(30));

		assertThat(queryService.tasks(workflowId, 2))
				.allSatisfy(task -> assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED));
		assertThat(queryService.tasks(workflowId, 1)).extracting(WorkflowTask::getCompletedAt)
				.containsExactlyElementsOf(version1TasksBeforeReplan.stream().map(WorkflowTask::getCompletedAt)
						.toList());
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder replanRequest() {
		return post("/api/workflows/{id}/replan", workflowId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"changedRequirement": "%s",
						 "reason": "Requirement changed after stakeholder feedback"}
						""".formatted(CHANGED));
	}
}
