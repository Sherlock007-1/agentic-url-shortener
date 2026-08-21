package com.agenticsdlc.orchestrator.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.domain.ClarificationStatus;
import com.agenticsdlc.orchestrator.domain.Decision;
import com.agenticsdlc.orchestrator.domain.TaskStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowGraphVersion;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowTask;
import com.agenticsdlc.orchestrator.engine.WorkflowQueryService;
import com.agenticsdlc.orchestrator.engine.WorkflowService;
import com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate;
import com.agenticsdlc.orchestrator.repository.ClarificationRequestRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowReplanRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import com.agenticsdlc.orchestrator.support.AbstractPostgresIntegrationTest;
import com.agenticsdlc.orchestrator.support.TestDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Ambiguous requirement scenario, end to end.
 *
 * <p>"Make shortened URLs more secure." must not be actioned autonomously. The
 * workflow is expected to stop and ask, be answered by a human, replan into graph
 * version 2, keep version 1 queryable, and then run to completion under the
 * clarified requirement - recording along the way that the clarified behaviour
 * already exists and needs no duplicate implementation.
 *
 * <p>Approval gates are disabled in the test profile, so the only thing that can
 * park this workflow is the clarification gate itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AmbiguousRequirementScenarioIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String AMBIGUOUS = "Make shortened URLs more secure.";

	private static final String CLARIFIED = "Reject unsafe URL schemes and require HTTP or HTTPS URLs with a valid host.";

	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private WorkflowService workflowService;

	@Autowired
	private WorkflowQueryService queryService;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Autowired
	private ClarificationRequestRepository clarificationRepository;

	@Autowired
	private WorkflowReplanRepository replanRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		TestDatabase.clean(jdbcTemplate);
	}

	@Test
	void theAmbiguousRequirementIsNotActionedAutonomously() {
		UUID workflowId = startAmbiguousWorkflow();

		WorkflowRun run = workflowRunRepository.findById(workflowId).orElseThrow();
		assertThat(run.getStatus()).isEqualTo(WorkflowStatus.AWAITING_CLARIFICATION);
		assertThat(run.getCompletedAt()).isNull();

		List<WorkflowTask> tasks = queryService.tasks(workflowId, 1);
		assertThat(taskStatus(tasks, SdlcWorkflowGraphTemplate.REQUIREMENT_ANALYSIS)).isEqualTo(TaskStatus.COMPLETED);
		// Everything downstream of the question is untouched: no guessed analysis,
		// no guessed plan, no guessed implementation.
		assertThat(taskStatus(tasks, SdlcWorkflowGraphTemplate.CODEBASE_ANALYSIS)).isEqualTo(TaskStatus.PENDING);
		assertThat(taskStatus(tasks, SdlcWorkflowGraphTemplate.PLANNING)).isEqualTo(TaskStatus.PENDING);
		assertThat(taskStatus(tasks, SdlcWorkflowGraphTemplate.IMPLEMENTATION)).isEqualTo(TaskStatus.PENDING);
	}

	@Test
	void theClarificationRequestAndItsRationaleArePersisted() throws Exception {
		UUID workflowId = startAmbiguousWorkflow();

		mockMvc.perform(get("/api/workflows/{id}/clarifications", workflowId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].status").value("PENDING"))
				.andExpect(jsonPath("$[0].question")
						.value(org.hamcrest.Matchers.containsString("Which security improvement is intended")))
				.andExpect(jsonPath("$[0].requestedAt").exists())
				.andExpect(jsonPath("$[0].answer").doesNotExist());

		assertThat(queryService.auditTrail(workflowId)).extracting(event -> event.getEventType())
				.contains(AuditEventType.CLARIFICATION_REQUESTED);
		assertThat(queryService.decisions(workflowId)).anySatisfy(decision -> {
			assertThat(decision.getDecisionType()).isEqualTo("CLARIFICATION");
			assertThat(decision.getTitle()).contains("ambiguous-security");
			assertThat(decision.getRationale()).contains("would mean guessing");
		});
	}

	@Test
	void answeringWithAReplanCreatesGraphVersionTwoAndKeepsVersionOne() throws Exception {
		UUID workflowId = startAmbiguousWorkflow();
		UUID clarificationId = pendingClarificationId(workflowId);
		List<UUID> version1TaskIds = queryService.tasks(workflowId, 1).stream().map(WorkflowTask::getId).toList();

		answer(workflowId, clarificationId, true)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ANSWERED"))
				.andExpect(jsonPath("$.answer").value(CLARIFIED))
				.andExpect(jsonPath("$.answeredBy").value("product-owner"))
				.andExpect(jsonPath("$.resolvedAt").exists());

		WorkflowRun run = workflowRunRepository.findById(workflowId).orElseThrow();
		assertThat(run.getCurrentGraphVersion()).isEqualTo(2);
		assertThat(run.getStatus()).isEqualTo(WorkflowStatus.READY);
		assertThat(workflowService.requirementText(run.getRequirementId())).isEqualTo(CLARIFIED);

		assertThat(clarificationRepository.findById(clarificationId).orElseThrow().getStatus())
				.isEqualTo(ClarificationStatus.ANSWERED);

		// Version 1 is preserved, not rewritten.
		assertThat(queryService.tasks(workflowId, 1)).extracting(WorkflowTask::getId)
				.containsExactlyElementsOf(version1TaskIds);
		assertThat(queryService.graphVersions(workflowId)).extracting(WorkflowGraphVersion::getVersion)
				.containsExactly(1, 2);

		mockMvc.perform(get("/api/workflows/{id}/graph", workflowId).param("version", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.tasks[?(@.taskKey=='requirement-analysis')].status")
						.value(org.hamcrest.Matchers.contains("COMPLETED")));
	}

	@Test
	void theReplanLineageExplainsWhyThePlanChanged() throws Exception {
		UUID workflowId = startAmbiguousWorkflow();
		UUID clarificationId = pendingClarificationId(workflowId);

		answer(workflowId, clarificationId, true).andExpect(status().isOk());

		assertThat(replanRepository.findByWorkflowRunIdOrderByCreatedAtAsc(workflowId)).singleElement()
				.satisfies(replan -> {
					assertThat(replan.getFromGraphVersion()).isEqualTo(1);
					assertThat(replan.getToGraphVersion()).isEqualTo(2);
					assertThat(replan.getClarificationId()).isEqualTo(clarificationId);
					assertThat(replan.getPreviousRequirement()).isEqualTo(AMBIGUOUS);
					assertThat(replan.getNewRequirement()).isEqualTo(CLARIFIED);
					assertThat(replan.getReason()).contains("Clarification answered");
				});
		assertThat(queryService.auditTrail(workflowId)).extracting(event -> event.getEventType())
				.contains(AuditEventType.CLARIFICATION_REQUESTED, AuditEventType.CLARIFICATION_ANSWERED,
						AuditEventType.REPLAN_STARTED, AuditEventType.GRAPH_CREATED, AuditEventType.REPLAN_COMPLETED);
		assertThat(queryService.decisions(workflowId)).anySatisfy(decision -> {
			assertThat(decision.getDecisionType()).isEqualTo("REPLAN");
			assertThat(decision.getRationale()).contains(AMBIGUOUS).contains(CLARIFIED);
		});
	}

	@Test
	void versionTwoCompletesAndRecordsThatNoDuplicateImplementationIsNeeded() throws Exception {
		UUID workflowId = startAmbiguousWorkflow();
		answer(workflowId, pendingClarificationId(workflowId), true).andExpect(status().isOk());

		workflowService.start(workflowId);
		awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.COMPLETED, TIMEOUT);

		assertThat(queryService.tasks(workflowId, 2))
				.allSatisfy(task -> assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED));

		List<Decision> decisions = queryService.decisions(workflowId);
		assertThat(decisions).anySatisfy(decision -> {
			assertThat(decision.getDecisionType()).isEqualTo("CODEBASE_ANALYSIS");
			assertThat(decision.getTitle()).contains("already exists");
			assertThat(decision.getRationale()).contains("UrlValidator");
		});
		assertThat(decisions).anySatisfy(decision -> {
			assertThat(decision.getDecisionType()).isEqualTo("IMPLEMENTATION");
			assertThat(decision.getTitle()).contains("No code change required");
		});
	}

	@Test
	void theSameQuestionIsNeverAskedTwice() throws Exception {
		UUID workflowId = startAmbiguousWorkflow();

		// Answering without replanning leaves the ambiguous text in place; the gate
		// must respect the human answer instead of parking the run again.
		answer(workflowId, pendingClarificationId(workflowId), false).andExpect(status().isOk());

		awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.COMPLETED, TIMEOUT);
		assertThat(clarificationRepository.findByWorkflowRunIdOrderByRequestedAtAsc(workflowId)).hasSize(1);
	}

	@Test
	void anUnambiguousRequirementIsNeverParkedByTheGate() {
		UUID workflowId = workflowService.createWorkflow("Add click analytics for shortened URLs.").getId();
		workflowService.start(workflowId);

		awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.COMPLETED, TIMEOUT);
		assertThat(clarificationRepository.findByWorkflowRunIdOrderByRequestedAtAsc(workflowId)).isEmpty();
		assertThat(queryService.graphVersions(workflowId)).hasSize(1);
	}

	@Test
	void theScenarioCanBeStartedReproduciblyThroughTheScenarioApi() throws Exception {
		String body = mockMvc.perform(post("/api/scenarios/{key}/start", ScenarioCatalog.AMBIGUOUS_SECURITY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.requirement").value(AMBIGUOUS))
				.andReturn().getResponse().getContentAsString();

		UUID workflowId = UUID.fromString(objectMapper.readTree(body).get("workflowId").asText());
		awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.AWAITING_CLARIFICATION, TIMEOUT);
		assertThat(clarificationRepository.findByWorkflowRunIdOrderByRequestedAtAsc(workflowId)).hasSize(1);
	}

	private UUID startAmbiguousWorkflow() {
		UUID workflowId = workflowService.createWorkflow(AMBIGUOUS).getId();
		workflowService.start(workflowId);
		awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.AWAITING_CLARIFICATION, TIMEOUT);
		return workflowId;
	}

	private UUID pendingClarificationId(UUID workflowId) {
		return clarificationRepository.findByWorkflowRunIdAndStatus(workflowId, ClarificationStatus.PENDING).stream()
				.findFirst()
				.orElseThrow(() -> new AssertionError("No pending clarification for workflow " + workflowId))
				.getId();
	}

	private org.springframework.test.web.servlet.ResultActions answer(UUID workflowId, UUID clarificationId,
			boolean replan) throws Exception {
		return mockMvc.perform(
				post("/api/workflows/{id}/clarifications/{clarificationId}/answer", workflowId, clarificationId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(java.util.Map.of(
								"answer", CLARIFIED,
								"answeredBy", "product-owner",
								"replan", replan))));
	}

	private TaskStatus taskStatus(List<WorkflowTask> tasks, String taskKey) {
		return tasks.stream()
				.filter(task -> task.getTaskKey().equals(taskKey))
				.findFirst()
				.orElseThrow(() -> new AssertionError("No task '" + taskKey + "' in the graph"))
				.getStatus();
	}
}
