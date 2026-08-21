package com.agenticsdlc.orchestrator.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.domain.ClarificationRequest;
import com.agenticsdlc.orchestrator.domain.ClarificationStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.engine.WorkflowQueryService;
import com.agenticsdlc.orchestrator.engine.WorkflowService;
import com.agenticsdlc.orchestrator.repository.ClarificationRequestRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowReplanRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import com.agenticsdlc.orchestrator.support.AbstractPostgresIntegrationTest;
import com.agenticsdlc.orchestrator.support.TestDatabase;
import java.time.Duration;
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
 * The clarification gate: the orchestrator can stop and ask instead of guessing.
 *
 * <p>Only the mechanism is tested here - the ambiguous-requirement scenario that
 * will use it is a later increment, so no scenario text is hard-coded.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClarificationGateIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String QUESTION = "Which retention period applies to the collected data?";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private WorkflowService workflowService;

	@Autowired
	private ClarificationService clarificationService;

	@Autowired
	private WorkflowQueryService queryService;

	@Autowired
	private ClarificationRequestRepository clarificationRepository;

	@Autowired
	private WorkflowReplanRepository replanRepository;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		TestDatabase.clean(jdbcTemplate);
	}

	@Test
	void askingParksTheWorkflowInAwaitingClarification() throws Exception {
		UUID workflowId = workflowService.createWorkflow("Add click analytics for shortened URLs.").getId();

		ClarificationRequest request = clarificationService.ask(workflowId, null, QUESTION);

		assertThat(workflowRunRepository.findById(workflowId).orElseThrow().getStatus())
				.isEqualTo(WorkflowStatus.AWAITING_CLARIFICATION);
		assertThat(request.getStatus()).isEqualTo(ClarificationStatus.PENDING);
		assertThat(request.getRequestedAt()).isNotNull();

		mockMvc.perform(get("/api/workflows/{id}/clarifications", workflowId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].question").value(QUESTION))
				.andExpect(jsonPath("$[0].status").value("PENDING"));
		assertThat(auditTypes(workflowId)).contains(AuditEventType.CLARIFICATION_REQUESTED);
	}

	@Test
	void answeringPersistsTheAnswerAndLetsTheWorkflowContinue() throws Exception {
		UUID workflowId = workflowService.createWorkflow("Add click analytics for shortened URLs.").getId();
		UUID clarificationId = clarificationService.ask(workflowId, null, QUESTION).getId();

		mockMvc.perform(post("/api/workflows/{id}/clarifications/{clarificationId}/answer", workflowId,
						clarificationId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"answer": "Retain click data for 90 days.", "answeredBy": "product-owner",
								 "replan": false}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ANSWERED"))
				.andExpect(jsonPath("$.answer").value("Retain click data for 90 days."))
				.andExpect(jsonPath("$.resolvedAt").exists());

		// Answering un-parks the run, which then executes its graph to completion.
		awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.COMPLETED, Duration.ofSeconds(30));
		ClarificationRequest reloaded = clarificationRepository.findById(clarificationId).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(ClarificationStatus.ANSWERED);
		assertThat(reloaded.getAnsweredBy()).isEqualTo("product-owner");
		assertThat(auditTypes(workflowId)).contains(AuditEventType.CLARIFICATION_ANSWERED);
	}

	@Test
	void anAnswerCanTriggerAReplanIntoANewGraphVersion() throws Exception {
		UUID workflowId = workflowService.createWorkflow("Add click analytics for shortened URLs.").getId();
		UUID clarificationId = clarificationService.ask(workflowId, null, QUESTION).getId();

		mockMvc.perform(post("/api/workflows/{id}/clarifications/{clarificationId}/answer", workflowId,
						clarificationId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"answer": "Add click analytics with a 90 day retention window.",
								 "answeredBy": "product-owner", "replan": true}
								"""))
				.andExpect(status().isOk());

		WorkflowRun run = workflowRunRepository.findById(workflowId).orElseThrow();
		assertThat(run.getCurrentGraphVersion()).isEqualTo(2);
		assertThat(run.getStatus()).isEqualTo(WorkflowStatus.READY);
		assertThat(replanRepository.findByWorkflowRunIdOrderByCreatedAtAsc(workflowId)).singleElement()
				.satisfies(replan -> {
					assertThat(replan.getFromGraphVersion()).isEqualTo(1);
					assertThat(replan.getToGraphVersion()).isEqualTo(2);
					assertThat(replan.getClarificationId()).isEqualTo(clarificationId);
					assertThat(replan.getNewRequirement()).contains("90 day retention window");
				});
		assertThat(auditTypes(workflowId)).contains(AuditEventType.REPLAN_STARTED, AuditEventType.REPLAN_COMPLETED);
	}

	private java.util.List<AuditEventType> auditTypes(UUID workflowId) {
		return queryService.auditTrail(workflowId).stream().map(event -> event.getEventType()).toList();
	}
}
