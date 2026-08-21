package com.agenticsdlc.orchestrator.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agenticsdlc.orchestrator.domain.Approval;
import com.agenticsdlc.orchestrator.domain.ApprovalGate;
import com.agenticsdlc.orchestrator.domain.ApprovalStatus;
import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.domain.TaskStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.engine.WorkflowQueryService;
import com.agenticsdlc.orchestrator.engine.WorkflowService;
import com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate;
import com.agenticsdlc.orchestrator.repository.ApprovalRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Human approval gates really block autonomous progression.
 *
 * <p>Gates are enabled here explicitly; the orchestration-core tests keep running
 * un-gated so their assertions stay untouched.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "orchestrator.governance.approval-gates=PRE_IMPLEMENTATION,FINAL")
class ApprovalGateIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final String REQUIREMENT = "Add click analytics for shortened URLs.";

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
	private ApprovalRepository approvalRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		TestDatabase.clean(jdbcTemplate);
	}

	@Test
	void implementationCannotStartBeforeThePreImplementationGateIsApproved() throws Exception {
		UUID workflowId = startAndWaitForApprovalGate();

		assertThat(taskStatus(workflowId, SdlcWorkflowGraphTemplate.ARCHITECTURE)).isEqualTo(TaskStatus.COMPLETED);
		assertThat(taskStatus(workflowId, SdlcWorkflowGraphTemplate.IMPLEMENTATION))
				.isEqualTo(TaskStatus.WAITING_FOR_APPROVAL);
		for (String downstream : List.of(SdlcWorkflowGraphTemplate.TESTS, SdlcWorkflowGraphTemplate.SECURITY,
				SdlcWorkflowGraphTemplate.DOCUMENTATION, SdlcWorkflowGraphTemplate.VALIDATION)) {
			assertThat(taskStatus(workflowId, downstream)).isEqualTo(TaskStatus.PENDING);
		}

		mockMvc.perform(get("/api/workflows/{id}/approvals", workflowId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].gate").value("PRE_IMPLEMENTATION"))
				.andExpect(jsonPath("$[0].status").value("PENDING"))
				.andExpect(jsonPath("$[0].requestedAt").exists());

		assertThat(auditTypes(workflowId)).contains(AuditEventType.APPROVAL_REQUESTED);
	}

	@Test
	void approvingReleasesTheWorkflowAndTheFinalGateBlocksCompletion() throws Exception {
		UUID workflowId = startAndWaitForApprovalGate();

		approve(workflowId, pendingApproval(workflowId, ApprovalGate.PRE_IMPLEMENTATION).getId(), "release-manager",
				"Architecture reviewed");

		// The run parks again in front of the final gate, with every task completed.
		WorkflowRun run = awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.WAITING_FOR_APPROVAL,
				Duration.ofSeconds(30));
		assertThat(run.getStatus()).isEqualTo(WorkflowStatus.WAITING_FOR_APPROVAL);
		assertThat(taskRepository.findByWorkflowRunIdOrderBySequenceNoAsc(workflowId))
				.allSatisfy(task -> assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED));
		assertThat(approvalRepository.findByWorkflowRunIdOrderByRequestedAtAsc(workflowId))
				.extracting(Approval::getGate)
				.containsExactly(ApprovalGate.PRE_IMPLEMENTATION, ApprovalGate.FINAL);

		approve(workflowId, pendingApproval(workflowId, ApprovalGate.FINAL).getId(), "product-owner", "Ship it");

		assertThat(awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.COMPLETED, Duration.ofSeconds(30))
				.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
		assertThat(approvalRepository.findByWorkflowRunIdOrderByRequestedAtAsc(workflowId))
				.allSatisfy(approval -> {
					assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
					assertThat(approval.getResolvedAt()).isNotNull();
					assertThat(approval.getReviewer()).isNotBlank();
				});
		assertThat(auditTypes(workflowId)).contains(AuditEventType.APPROVAL_GRANTED, AuditEventType.WORKFLOW_COMPLETED);
	}

	@Test
	void rejectingAnApprovalSafeStopsTheWorkflowWithAnAuditableReason() throws Exception {
		UUID workflowId = startAndWaitForApprovalGate();
		UUID approvalId = pendingApproval(workflowId, ApprovalGate.PRE_IMPLEMENTATION).getId();

		mockMvc.perform(post("/api/workflows/{id}/approvals/{approvalId}/reject", workflowId, approvalId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"reviewer": "security-officer", "comment": "Design not acceptable"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"));

		WorkflowRun run = workflowRunRepository.findById(workflowId).orElseThrow();
		assertThat(run.getStatus()).isEqualTo(WorkflowStatus.SAFE_STOPPED);
		assertThat(run.getSafeStopReason()).contains("PRE_IMPLEMENTATION").contains("security-officer");
		assertThat(taskStatus(workflowId, SdlcWorkflowGraphTemplate.IMPLEMENTATION))
				.isNotEqualTo(TaskStatus.COMPLETED);
		assertThat(auditTypes(workflowId)).contains(AuditEventType.APPROVAL_REJECTED,
				AuditEventType.WORKFLOW_SAFE_STOPPED);
		assertThat(queryService.decisions(workflowId))
				.anySatisfy(decision -> assertThat(decision.getTitle()).contains("rejected"));
	}

	@Test
	void governanceStateSurvivesAFreshRepositoryRead() throws Exception {
		UUID workflowId = startAndWaitForApprovalGate();

		// Nothing is cached in memory: a new read sees the same parked state.
		assertThat(workflowRunRepository.findById(workflowId).orElseThrow().getStatus())
				.isEqualTo(WorkflowStatus.WAITING_FOR_APPROVAL);
		assertThat(approvalRepository.findByWorkflowRunIdAndStatus(workflowId, ApprovalStatus.PENDING)).hasSize(1);
	}

	private UUID startAndWaitForApprovalGate() {
		UUID workflowId = workflowService.createWorkflow(REQUIREMENT).getId();
		workflowService.start(workflowId);
		awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.WAITING_FOR_APPROVAL, Duration.ofSeconds(30));
		return workflowId;
	}

	private void approve(UUID workflowId, UUID approvalId, String reviewer, String comment) throws Exception {
		mockMvc.perform(post("/api/workflows/{id}/approvals/{approvalId}/approve", workflowId, approvalId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reviewer\": \"" + reviewer + "\", \"comment\": \"" + comment + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"));
	}

	private Approval pendingApproval(UUID workflowId, ApprovalGate gate) {
		return approvalRepository.findByWorkflowRunIdAndStatus(workflowId, ApprovalStatus.PENDING).stream()
				.filter(approval -> approval.getGate() == gate)
				.findFirst()
				.orElseThrow(() -> new AssertionError("No pending approval for gate " + gate));
	}

	private TaskStatus taskStatus(UUID workflowId, String taskKey) {
		return taskRepository.findByWorkflowRunIdAndTaskKey(workflowId, taskKey).orElseThrow().getStatus();
	}

	private List<AuditEventType> auditTypes(UUID workflowId) {
		return queryService.auditTrail(workflowId).stream().map(event -> event.getEventType()).toList();
	}
}
