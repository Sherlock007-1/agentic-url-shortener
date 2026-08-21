package com.agenticsdlc.orchestrator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.agenticsdlc.orchestrator.domain.TaskStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowTask;
import com.agenticsdlc.orchestrator.engine.WorkflowTransitionService.ClaimedTask;
import com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowTaskRepository;
import com.agenticsdlc.orchestrator.support.AbstractPostgresIntegrationTest;
import com.agenticsdlc.orchestrator.support.TestDatabase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;


/**
 * Drives the engine's eligibility rules step by step, without any worker threads,
 * so sequential ordering and the three-way join are proven deterministically from
 * persisted state instead of from timing.
 */
@SpringBootTest
class WorkflowSchedulingRulesIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private WorkflowService workflowService;

	@Autowired
	private WorkflowTransitionService transitionService;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Autowired
	private WorkflowTaskRepository taskRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private UUID workflowId;

	@BeforeEach
	void setUp() {
		TestDatabase.clean(jdbcTemplate);
		workflowId = workflowService.createWorkflow("Add click analytics for shortened URLs.").getId();
		transitionService.startWorkflow(workflowId);
	}

	@Test
	void onlyTheEntryTaskIsEligibleAtTheStart() {
		List<ClaimedTask> claimed = transitionService.claimEligibleTasks(workflowId);

		assertThat(claimed).extracting(ClaimedTask::taskKey)
				.containsExactly(SdlcWorkflowGraphTemplate.REQUIREMENT_ANALYSIS);
		assertThat(status(SdlcWorkflowGraphTemplate.CODEBASE_ANALYSIS)).isEqualTo(TaskStatus.PENDING);
	}

	@Test
	void aSuccessorStaysPendingUntilItsPredecessorCompletes() {
		ClaimedTask requirementAnalysis = claimSingle();

		// Predecessor is RUNNING, not COMPLETED: nothing else may be claimed.
		assertThat(transitionService.claimEligibleTasks(workflowId)).isEmpty();
		assertThat(status(SdlcWorkflowGraphTemplate.CODEBASE_ANALYSIS)).isEqualTo(TaskStatus.PENDING);

		complete(requirementAnalysis);

		assertThat(transitionService.claimEligibleTasks(workflowId)).extracting(ClaimedTask::taskKey)
				.containsExactly(SdlcWorkflowGraphTemplate.CODEBASE_ANALYSIS);
	}

	@Test
	void implementationFansOutIntoThreeIndependentlyEligibleTasks() {
		runSpineThroughImplementation();

		List<ClaimedTask> claimed = transitionService.claimEligibleTasks(workflowId);

		assertThat(claimed).extracting(ClaimedTask::taskKey).containsExactlyInAnyOrder(
				SdlcWorkflowGraphTemplate.TESTS, SdlcWorkflowGraphTemplate.SECURITY,
				SdlcWorkflowGraphTemplate.DOCUMENTATION);
		assertThat(status(SdlcWorkflowGraphTemplate.VALIDATION)).isEqualTo(TaskStatus.PENDING);
	}

	@Test
	void validationWaitsForAllThreeBranchesBeforeBecomingEligible() {
		runSpineThroughImplementation();
		List<ClaimedTask> branches = transitionService.claimEligibleTasks(workflowId);

		complete(branchNamed(branches, SdlcWorkflowGraphTemplate.TESTS));
		assertThat(transitionService.claimEligibleTasks(workflowId)).isEmpty();

		complete(branchNamed(branches, SdlcWorkflowGraphTemplate.SECURITY));
		assertThat(transitionService.claimEligibleTasks(workflowId))
				.as("validation must not start with two of three branches complete")
				.isEmpty();
		assertThat(status(SdlcWorkflowGraphTemplate.VALIDATION)).isEqualTo(TaskStatus.PENDING);

		complete(branchNamed(branches, SdlcWorkflowGraphTemplate.DOCUMENTATION));

		List<ClaimedTask> join = transitionService.claimEligibleTasks(workflowId);
		assertThat(join).extracting(ClaimedTask::taskKey).containsExactly(SdlcWorkflowGraphTemplate.VALIDATION);
		assertThat(join.get(0).upstreamOutputs()).containsOnlyKeys(SdlcWorkflowGraphTemplate.TESTS,
				SdlcWorkflowGraphTemplate.SECURITY, SdlcWorkflowGraphTemplate.DOCUMENTATION);
	}

	@Test
	void workflowCompletesOnlyAfterTheFinalTask() {
		runSpineThroughImplementation();
		transitionService.claimEligibleTasks(workflowId).forEach(this::complete);
		ClaimedTask validation = claimSingle();

		assertThat(transitionService.finalizeIfFinished(workflowId)).isFalse();
		assertThat(workflowRunRepository.findById(workflowId).orElseThrow().getStatus())
				.isEqualTo(WorkflowStatus.RUNNING);

		complete(validation);

		assertThat(transitionService.finalizeIfFinished(workflowId)).isTrue();
		WorkflowRun run = workflowRunRepository.findById(workflowId).orElseThrow();
		assertThat(run.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
		assertThat(run.getCompletedAt()).isNotNull();
	}

	private void runSpineThroughImplementation() {
		for (int i = 0; i < 5; i++) {
			complete(claimSingle());
		}
		assertThat(status(SdlcWorkflowGraphTemplate.IMPLEMENTATION)).isEqualTo(TaskStatus.COMPLETED);
	}

	private ClaimedTask claimSingle() {
		List<ClaimedTask> claimed = transitionService.claimEligibleTasks(workflowId);
		assertThat(claimed).hasSize(1);
		return claimed.get(0);
	}

	private ClaimedTask branchNamed(List<ClaimedTask> claimed, String taskKey) {
		return claimed.stream().filter(task -> task.taskKey().equals(taskKey)).findFirst().orElseThrow();
	}

	private void complete(ClaimedTask task) {
		transitionService.completeTask(task.taskId(), "output of " + task.taskKey(), "summary", List.of(),
				Map.of());
	}

	private TaskStatus status(String taskKey) {
		return taskRepository.findByWorkflowRunIdAndTaskKey(workflowId, taskKey)
				.map(WorkflowTask::getStatus)
				.orElseThrow();
	}
}
