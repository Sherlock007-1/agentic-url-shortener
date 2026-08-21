package com.agenticsdlc.orchestrator.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import com.agenticsdlc.orchestrator.support.AbstractPostgresIntegrationTest;
import com.agenticsdlc.orchestrator.support.TestDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** End-to-end API test covering the demo flow exposed in Swagger. */
@SpringBootTest
@AutoConfigureMockMvc
class OrchestratorApiIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		TestDatabase.clean(jdbcTemplate);
	}

	@Test
	void submitRequirementStartWorkflowAndInspectResults() throws Exception {
		String created = mockMvc.perform(post("/api/requirements")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"text": "Add click analytics for shortened URLs."}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("READY"))
				.andExpect(jsonPath("$.graphVersion").value(1))
				.andReturn().getResponse().getContentAsString();

		UUID workflowId = UUID.fromString(objectMapper.readTree(created).get("workflowId").asText());

		mockMvc.perform(get("/api/workflows/{id}/graph", workflowId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.tasks.length()").value(9))
				.andExpect(jsonPath("$.tasks[?(@.taskKey=='validation')].dependsOn[*]")
						.value(org.hamcrest.Matchers.containsInAnyOrder("documentation", "security", "tests")));

		mockMvc.perform(post("/api/workflows/{id}/start", workflowId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RUNNING"));

		awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.COMPLETED, Duration.ofSeconds(30));

		mockMvc.perform(get("/api/workflows/{id}", workflowId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.requirement").value("Add click analytics for shortened URLs."));

		mockMvc.perform(get("/api/workflows/{id}/tasks", workflowId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(9))
				.andExpect(jsonPath("$[?(@.taskKey=='" + SdlcWorkflowGraphTemplate.VALIDATION + "')].status")
						.value(org.hamcrest.Matchers.contains("COMPLETED")));

		mockMvc.perform(get("/api/workflows/{id}/decisions", workflowId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));

		String audit = mockMvc.perform(get("/api/workflows/{id}/audit", workflowId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].eventType").value("WORKFLOW_CREATED"))
				.andReturn().getResponse().getContentAsString();
		assertThat(audit).contains("WORKFLOW_COMPLETED");
	}

	@Test
	void unknownWorkflowReturns404() throws Exception {
		mockMvc.perform(get("/api/workflows/{id}", UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Not found"));
	}

	@Test
	void blankRequirementIsRejected() throws Exception {
		mockMvc.perform(post("/api/requirements")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"text": "  "}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void startingAnAlreadyCompletedWorkflowIsRejected() throws Exception {
		String created = mockMvc.perform(post("/api/requirements")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"text": "Add click analytics for shortened URLs."}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		UUID workflowId = UUID.fromString(objectMapper.readTree(created).get("workflowId").asText());

		mockMvc.perform(post("/api/workflows/{id}/start", workflowId)).andExpect(status().isOk());
		awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.COMPLETED, Duration.ofSeconds(30));

		mockMvc.perform(post("/api/workflows/{id}/start", workflowId))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Illegal workflow state"));
	}

	@Test
	void openApiDocumentExposesTheOrchestrationApi() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/requirements']").exists())
				.andExpect(jsonPath("$.paths['/api/workflows/{workflowId}/graph']").exists())
				.andExpect(jsonPath("$.paths['/api/workflows/{workflowId}/audit']").exists());
	}
}
