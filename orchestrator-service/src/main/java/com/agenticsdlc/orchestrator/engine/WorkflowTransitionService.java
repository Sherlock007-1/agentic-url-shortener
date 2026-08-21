package com.agenticsdlc.orchestrator.engine;

import com.agenticsdlc.orchestrator.agent.AgentDecision;
import com.agenticsdlc.orchestrator.domain.AgentExecution;
import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.domain.Decision;
import com.agenticsdlc.orchestrator.domain.TaskDependency;
import com.agenticsdlc.orchestrator.domain.TaskStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowTask;
import com.agenticsdlc.orchestrator.repository.AgentExecutionRepository;
import com.agenticsdlc.orchestrator.repository.DecisionRepository;
import com.agenticsdlc.orchestrator.repository.RequirementRepository;
import com.agenticsdlc.orchestrator.repository.TaskDependencyRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowTaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns every persisted workflow/task state transition.
 *
 * <p>Each public method is a short transaction, which keeps database locks away
 * from the (potentially slow) agent execution that happens in between.
 */
@Service
public class WorkflowTransitionService {

	private final WorkflowRunRepository workflowRunRepository;
	private final WorkflowTaskRepository taskRepository;
	private final TaskDependencyRepository dependencyRepository;
	private final AgentExecutionRepository agentExecutionRepository;
	private final DecisionRepository decisionRepository;
	private final RequirementRepository requirementRepository;
	private final AuditService auditService;
	private final ContextSerializer contextSerializer;
	private final Clock clock;

	public WorkflowTransitionService(WorkflowRunRepository workflowRunRepository, WorkflowTaskRepository taskRepository,
			TaskDependencyRepository dependencyRepository, AgentExecutionRepository agentExecutionRepository,
			DecisionRepository decisionRepository, RequirementRepository requirementRepository,
			AuditService auditService, ContextSerializer contextSerializer, Clock clock) {
		this.workflowRunRepository = workflowRunRepository;
		this.taskRepository = taskRepository;
		this.dependencyRepository = dependencyRepository;
		this.agentExecutionRepository = agentExecutionRepository;
		this.decisionRepository = decisionRepository;
		this.requirementRepository = requirementRepository;
		this.auditService = auditService;
		this.contextSerializer = contextSerializer;
		this.clock = clock;
	}

	/**
	 * Transitions a READY workflow to RUNNING. Starting an already running workflow
	 * is a no-op so the API stays idempotent.
	 */
	@Transactional
	public WorkflowRun startWorkflow(UUID workflowRunId) {
		WorkflowRun run = requireRun(workflowRunId);
		if (run.getStatus() == WorkflowStatus.RUNNING) {
			return run;
		}
		if (run.getStatus() != WorkflowStatus.READY) {
			throw new IllegalWorkflowStateException(
					"Workflow " + workflowRunId + " cannot be started from status " + run.getStatus());
		}
		run.markRunning(clock.instant());
		workflowRunRepository.save(run);
		auditService.record(run.getId(), null, AuditEventType.WORKFLOW_STARTED, "Workflow started");
		return run;
	}

	/**
	 * Promotes every task whose dependencies are satisfied to READY and then claims
	 * them as RUNNING, snapshotting the upstream context on each claimed task.
	 *
	 * <p>A task with several predecessors (the join) is only promoted once <em>all</em>
	 * of them are COMPLETED, so the join rule lives in data, not in code paths.
	 *
	 * @return descriptors of the tasks that this call claimed for execution
	 */
	@Transactional
	public List<ClaimedTask> claimEligibleTasks(UUID workflowRunId) {
		WorkflowRun run = requireRun(workflowRunId);
		if (run.getStatus() != WorkflowStatus.RUNNING) {
			return List.of();
		}

		List<WorkflowTask> tasks = taskRepository.findByWorkflowRunIdOrderBySequenceNoAsc(workflowRunId);
		Map<UUID, WorkflowTask> tasksById = tasks.stream()
				.collect(Collectors.toMap(WorkflowTask::getId, task -> task));
		Map<UUID, List<UUID>> dependencies = dependenciesByTask(tasks);
		String requirementText = requirementText(run);

		List<ClaimedTask> claimed = new ArrayList<>();
		for (WorkflowTask task : tasks) {
			if (task.getStatus() != TaskStatus.PENDING && task.getStatus() != TaskStatus.READY) {
				continue;
			}
			List<UUID> predecessors = dependencies.getOrDefault(task.getId(), List.of());
			boolean allCompleted = predecessors.stream()
					.map(tasksById::get)
					.allMatch(predecessor -> predecessor != null && predecessor.getStatus() == TaskStatus.COMPLETED);
			if (!allCompleted) {
				continue;
			}

			if (task.getStatus() == TaskStatus.PENDING) {
				task.markReady();
				auditService.record(workflowRunId, task.getId(), AuditEventType.TASK_READY,
						"Task '" + task.getTaskKey() + "' is ready");
			}

			Map<String, String> upstreamOutputs = new LinkedHashMap<>();
			for (UUID predecessorId : predecessors) {
				WorkflowTask predecessor = tasksById.get(predecessorId);
				upstreamOutputs.put(predecessor.getTaskKey(),
						predecessor.getOutputContext() == null ? "" : predecessor.getOutputContext());
			}

			task.markRunning(contextSerializer.write(upstreamOutputs), clock.instant());
			taskRepository.save(task);
			auditService.record(workflowRunId, task.getId(), AuditEventType.TASK_STARTED,
					"Task '" + task.getTaskKey() + "' started with agent " + task.getAgentType());
			claimed.add(new ClaimedTask(task.getId(), task.getTaskKey(), task.getAgentType(), requirementText,
					upstreamOutputs));
		}
		return claimed;
	}

	/** Persists a successful agent execution, its output context and its decisions. */
	@Transactional
	public void completeTask(UUID taskId, String output, String summary, List<AgentDecision> decisions,
			Map<String, String> inputContext) {
		Instant now = clock.instant();
		WorkflowTask task = requireTask(taskId);
		task.markCompleted(output, now);
		taskRepository.save(task);

		AgentExecution execution = new AgentExecution(task.getWorkflowRunId(), task.getId(), task.getAgentType(),
				contextSerializer.write(inputContext), now);
		execution.succeed(output, now);
		agentExecutionRepository.save(execution);

		for (AgentDecision decision : decisions) {
			decisionRepository.save(new Decision(task.getWorkflowRunId(), task.getId(), decision.type(),
					decision.title(), decision.rationale(), now));
			auditService.record(task.getWorkflowRunId(), task.getId(), AuditEventType.DECISION_RECORDED,
					"Decision recorded: " + decision.title());
		}

		auditService.record(task.getWorkflowRunId(), task.getId(), AuditEventType.TASK_COMPLETED,
				"Task '" + task.getTaskKey() + "' completed", summary);
	}

	/** Persists a failed agent execution and fails the whole workflow. */
	@Transactional
	public void failTask(UUID taskId, String errorMessage, Map<String, String> inputContext) {
		Instant now = clock.instant();
		WorkflowTask task = requireTask(taskId);
		task.markFailed(errorMessage, now);
		taskRepository.save(task);

		AgentExecution execution = new AgentExecution(task.getWorkflowRunId(), task.getId(), task.getAgentType(),
				contextSerializer.write(inputContext), now);
		execution.fail(errorMessage, now);
		agentExecutionRepository.save(execution);

		auditService.record(task.getWorkflowRunId(), task.getId(), AuditEventType.TASK_FAILED,
				"Task '" + task.getTaskKey() + "' failed", errorMessage);

		WorkflowRun run = requireRun(task.getWorkflowRunId());
		if (!run.getStatus().isTerminal()) {
			run.markFailed("Task '" + task.getTaskKey() + "' failed: " + errorMessage, now);
			workflowRunRepository.save(run);
			auditService.record(run.getId(), task.getId(), AuditEventType.WORKFLOW_FAILED, "Workflow failed");
		}
	}

	/**
	 * Completes the workflow once every task reached a terminal state.
	 *
	 * @return true when the workflow is terminal after this call
	 */
	@Transactional
	public boolean finalizeIfFinished(UUID workflowRunId) {
		WorkflowRun run = requireRun(workflowRunId);
		if (run.getStatus().isTerminal()) {
			return true;
		}
		List<WorkflowTask> tasks = taskRepository.findByWorkflowRunIdOrderBySequenceNoAsc(workflowRunId);
		if (tasks.isEmpty() || tasks.stream().anyMatch(task -> !task.getStatus().isTerminal())) {
			return false;
		}
		Instant now = clock.instant();
		if (tasks.stream().anyMatch(task -> task.getStatus() == TaskStatus.FAILED)) {
			run.markFailed("At least one task failed", now);
			workflowRunRepository.save(run);
			auditService.record(run.getId(), null, AuditEventType.WORKFLOW_FAILED, "Workflow failed");
		}
		else {
			run.markCompleted(now);
			workflowRunRepository.save(run);
			auditService.record(run.getId(), null, AuditEventType.WORKFLOW_COMPLETED, "Workflow completed");
		}
		return true;
	}

	private Map<UUID, List<UUID>> dependenciesByTask(List<WorkflowTask> tasks) {
		List<UUID> taskIds = tasks.stream().map(WorkflowTask::getId).toList();
		Map<UUID, List<UUID>> dependencies = new HashMap<>();
		if (taskIds.isEmpty()) {
			return dependencies;
		}
		for (TaskDependency edge : dependencyRepository.findByTaskIdIn(taskIds)) {
			dependencies.computeIfAbsent(edge.getTaskId(), key -> new ArrayList<>()).add(edge.getDependsOnTaskId());
		}
		return dependencies;
	}

	private String requirementText(WorkflowRun run) {
		return requirementRepository.findById(run.getRequirementId())
				.map(requirement -> requirement.getText())
				.orElseThrow(() -> new WorkflowNotFoundException("Requirement " + run.getRequirementId() + " not found"));
	}

	private WorkflowRun requireRun(UUID workflowRunId) {
		return workflowRunRepository.findById(workflowRunId)
				.orElseThrow(() -> new WorkflowNotFoundException("Workflow " + workflowRunId + " not found"));
	}

	private WorkflowTask requireTask(UUID taskId) {
		return taskRepository.findById(taskId)
				.orElseThrow(() -> new WorkflowNotFoundException("Task " + taskId + " not found"));
	}

	/** Immutable descriptor of a task claimed for execution. */
	public record ClaimedTask(UUID taskId, String taskKey, com.agenticsdlc.orchestrator.domain.AgentType agentType,
			String requirement, Map<String, String> upstreamOutputs) {

		public ClaimedTask {
			upstreamOutputs = Map.copyOf(upstreamOutputs);
		}
	}
}
