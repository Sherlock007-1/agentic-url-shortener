package com.agenticsdlc.orchestrator.api;

import com.agenticsdlc.orchestrator.api.dto.AuditEventResponse;
import com.agenticsdlc.orchestrator.api.dto.DecisionResponse;
import com.agenticsdlc.orchestrator.api.dto.GraphResponse;
import com.agenticsdlc.orchestrator.api.dto.GraphVersionResponse;
import com.agenticsdlc.orchestrator.api.dto.SnapshotResponse;
import com.agenticsdlc.orchestrator.api.dto.TaskResponse;
import com.agenticsdlc.orchestrator.api.dto.WorkflowResponse;
import com.agenticsdlc.orchestrator.domain.WorkflowGraphVersion;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowTask;
import com.agenticsdlc.orchestrator.engine.WorkflowQueryService;
import com.agenticsdlc.orchestrator.engine.WorkflowService;
import com.agenticsdlc.orchestrator.governance.WorkspaceSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Workflow lifecycle and read APIs. */
@RestController
@RequestMapping("/api/workflows")
@Tag(name = "Workflows", description = "Start workflows and inspect graph, tasks, decisions and audit trail")
public class WorkflowController {

	private final WorkflowService workflowService;
	private final WorkflowQueryService queryService;
	private final WorkspaceSnapshotService snapshotService;

	public WorkflowController(WorkflowService workflowService, WorkflowQueryService queryService,
			WorkspaceSnapshotService snapshotService) {
		this.workflowService = workflowService;
		this.queryService = queryService;
		this.snapshotService = snapshotService;
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
	@Operation(summary = "Persisted graph version with tasks, statuses and dependencies (defaults to the current version)")
	public GraphResponse graph(@PathVariable UUID workflowId, @RequestParam(required = false) Integer version) {
		WorkflowRun run = workflowService.getWorkflow(workflowId);
		int requested = version != null ? version : Math.max(run.getCurrentGraphVersion(), 1);
		WorkflowGraphVersion graphVersion = queryService.graphVersion(workflowId, requested);
		return new GraphResponse(workflowId, graphVersion.getVersion(), graphVersion.getDescription(),
				graphVersion.getCreatedAt(), tasksOfVersion(workflowId, requested));
	}

	@GetMapping("/{workflowId}/graph/versions")
	@Operation(summary = "History of graph versions; replanning adds versions instead of overwriting them")
	public List<GraphVersionResponse> graphVersions(@PathVariable UUID workflowId) {
		WorkflowRun run = workflowService.getWorkflow(workflowId);
		return queryService.graphVersions(workflowId).stream()
				.map(version -> GraphVersionResponse.from(version, queryService.tasks(workflowId,
						version.getVersion()).size(), version.getVersion() == run.getCurrentGraphVersion()))
				.toList();
	}

	@GetMapping("/{workflowId}/tasks")
	@Operation(summary = "Task execution details of the current graph version including persisted context")
	public List<TaskResponse> tasks(@PathVariable UUID workflowId) {
		WorkflowRun run = workflowService.getWorkflow(workflowId);
		return tasksOfVersion(workflowId, Math.max(run.getCurrentGraphVersion(), 1));
	}

	@GetMapping("/{workflowId}/decisions")
	@Operation(summary = "Decision lineage recorded by agents and governance actions")
	public List<DecisionResponse> decisions(@PathVariable UUID workflowId) {
		return queryService.decisions(workflowId).stream().map(DecisionResponse::from).toList();
	}

	@GetMapping("/{workflowId}/audit")
	@Operation(summary = "Ordered audit history of the workflow")
	public List<AuditEventResponse> audit(@PathVariable UUID workflowId) {
		return queryService.auditTrail(workflowId).stream().map(AuditEventResponse::from).toList();
	}

	@GetMapping("/{workflowId}/snapshots")
	@Operation(summary = "Workspace snapshots and their rollback outcome")
	public List<SnapshotResponse> snapshots(@PathVariable UUID workflowId) {
		return snapshotService.snapshots(workflowId).stream().map(SnapshotResponse::from).toList();
	}

	@PostMapping("/{workflowId}/snapshots/{snapshotId}/rollback")
	@Operation(summary = "Restore the workspace of a run from a snapshot")
	public SnapshotResponse rollback(@PathVariable UUID workflowId, @PathVariable UUID snapshotId) {
		return SnapshotResponse.from(snapshotService.rollback(workflowId, snapshotId));
	}

	private List<TaskResponse> tasksOfVersion(UUID workflowId, int version) {
		List<WorkflowTask> tasks = queryService.tasks(workflowId, version);
		Map<String, List<String>> dependencies = queryService.dependencyKeys(tasks);
		return tasks.stream()
				.map(task -> TaskResponse.from(task, dependencies.getOrDefault(task.getTaskKey(), List.of())))
				.toList();
	}

	private WorkflowResponse toResponse(WorkflowRun run) {
		return WorkflowResponse.from(run, workflowService.requirementText(run.getRequirementId()));
	}
}