package com.agenticsdlc.orchestrator.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.domain.TaskStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.engine.WorkflowQueryService;
import com.agenticsdlc.orchestrator.engine.WorkflowService;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowTaskRepository;
import com.agenticsdlc.orchestrator.support.AbstractPostgresIntegrationTest;
import com.agenticsdlc.orchestrator.support.TestDatabase;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Autonomy boundary: a run that exceeds its wall-clock budget is stopped in a
 * controlled way instead of continuing to consume the worker pool.
 *
 * <p>The budget is configured as already exhausted ({@code PT-1S}), which makes the
 * assertion deterministic instead of sleep-based.
 */
@SpringBootTest
@TestPropertySource(properties = "orchestrator.governance.max-workflow-duration=PT-1S")
class WorkflowBudgetIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private WorkflowService workflowService;

	@Autowired
	private WorkflowQueryService queryService;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Autowired
	private WorkflowTaskRepository taskRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		TestDatabase.clean(jdbcTemplate);
	}

	@Test
	void exceedingTheWallClockBudgetSafeStopsTheWorkflowWithEvidence() {
		UUID workflowId = workflowService.createWorkflow("Add click analytics for shortened URLs.").getId();

		workflowService.start(workflowId);

		WorkflowRun run = awaitStatus(workflowRunRepository, workflowId, WorkflowStatus.SAFE_STOPPED,
				Duration.ofSeconds(30));
		assertThat(run.getSafeStopReason()).contains("Workflow wall-clock budget exceeded");
		assertThat(run.getStatus().isTerminal()).isTrue();
		assertThat(taskRepository.findByWorkflowRunIdOrderBySequenceNoAsc(workflowId))
				.allSatisfy(task -> assertThat(task.getStatus()).isNotEqualTo(TaskStatus.RUNNING));
		assertThat(queryService.auditTrail(workflowId)).extracting(event -> event.getEventType())
				.contains(AuditEventType.BUDGET_EXCEEDED, AuditEventType.WORKFLOW_SAFE_STOPPED);
	}
}
