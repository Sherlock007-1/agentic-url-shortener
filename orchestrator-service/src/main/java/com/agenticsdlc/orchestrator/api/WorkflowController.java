package com.agenticsdlc.orchestrator.api;

import com.agenticsdlc.orchestrator.api.dto.AuditEventResponse;
import com.agenticsdlc.orchestrator.api.dto.DecisionResponse;
import com.agenticsdlc.orchestrator.api.dto.GraphResponse;
import com.agenticsdlc.orchestrator.api.dto.TaskResponse;
import com.agenticsdlc.orchestrator.api.dto.WorkflowResponse;
import com.agenticsdlc.orchestrator.domain.WorkflowGraphVersion;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowTask;
import com.agenticsdlc.orchestrator.engine.WorkflowQueryService;
import com.agenticsdlc.orchestrator.engine.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Workflow lifecycle and read APIs. */
@RestController
@RequestMapping("/api/workflows")
@Tag(name = "Workflows", description = "Start workflows and inspect graph, tasks, decisions and audit trail")
public class WorkflowController {

	private final WorkflowService workflowService;
	private final WorkflowQueryService queryService;

	public WorkflowController(WorkflowService workflowService, WorkflowQueryService queryService) {
		this.workflowService = workflowService;
		this.queryService = queryService;
	}

	@PostMapping("/{workflowId}/start")
	@Operation(summary = "Start a workflow that is READY (idempotent while RUNNING)")
	public WorkflowResponse start(@PathVariable UUID workflowId) {
		WorkflowRun run = workflowService.start(workflowId);
		return toResponse(run);
	}

	@GetMapping("/{workflowId}")
	@Operation(summary = "Workflow summary and status")
	public WorkflowResponse get(@PathVariable UUID workflowId) {
		return toResponse(workflowService.getWorkflow(workflowId));
	}

	@GetMapping("/{workflowId}/graph")
	@Operation(summary = "Persisted graph version with tasks, statuses and dependencies")
	public GraphResponse graph(@PathVariable UUID workflowId) {
		WorkflowRun run = workflowService.getWorkflow(workflowId);
		WorkflowGraphVersion graphVersion = queryService.graphVersion(workflowId,
				Math.max(run.getCurrentGraphVersion(), 1));
		return new GraphResponse(workflowId, graphVersion.getVersion(), graphVersion.getDescription(),
				graphVersion.getCreatedAt(), tasks(workflowId));
	}

	@GetMapping("/{workflowId}/tasks")
	@Operation(summary = "Task execution details including persisted context")
	public List<TaskResponse> tasks(@PathVariable UUID workflowId) {
		List<WorkflowTask> tasks = queryService.tasks(workflowId);
		Map<String, List<String>> dependencies = queryService.dependencyKeys(tasks);
		return tasks.stream()
				.map(task -> TaskResponse.from(task, dependencies.getOrDefault(task.getTaskKey(), List.of())))
				.toList();
	}

	@GetMapping("/{workflowId}/decisions")
	@Operation(summary = "Decision lineage recorded by planning and architecture agents")
	public List<DecisionResponse> decisions(@PathVariable UUID workflowId) {
		return queryService.decisions(workflowId).stream().map(DecisionResponse::from).toList();
	}

	@GetMapping("/{workflowId}/audit")
	@Operation(summary = "Ordered audit history of the workflow")
	public List<AuditEventResponse> audit(@PathVariable UUID workflowId) {
		return queryService.auditTrail(workflowId).stream().map(AuditEventResponse::from).toList();
	}

	private WorkflowResponse toResponse(WorkflowRun run) {
		return WorkflowResponse.from(run, workflowService.requirementText(run.getRequirementId()));
	}
}
