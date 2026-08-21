package com.agenticsdlc.orchestrator.engine;

import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.domain.Requirement;
import com.agenticsdlc.orchestrator.domain.WorkflowGraphVersion;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.repository.RequirementRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case entry point: turns a requirement into a runnable workflow and starts it.
 */
@Service
public class WorkflowService {

	private final RequirementRepository requirementRepository;
	private final WorkflowRunRepository workflowRunRepository;
	private final WorkflowGraphService graphService;
	private final WorkflowTransitionService transitionService;
	private final AuditService auditService;
	private final WorkflowEngine engine;
	private final Clock clock;

	public WorkflowService(RequirementRepository requirementRepository, WorkflowRunRepository workflowRunRepository,
			WorkflowGraphService graphService, WorkflowTransitionService transitionService, AuditService auditService,
			WorkflowEngine engine, Clock clock) {
		this.requirementRepository = requirementRepository;
		this.workflowRunRepository = workflowRunRepository;
		this.graphService = graphService;
		this.transitionService = transitionService;
		this.auditService = auditService;
		this.engine = engine;
		this.clock = clock;
	}

	/** Creates requirement, workflow run and graph version 1 in one transaction. */
	@Transactional
	public WorkflowRun createWorkflow(String requirementText) {
		Requirement requirement = requirementRepository.save(new Requirement(requirementText, clock.instant()));
		WorkflowRun run = workflowRunRepository.save(new WorkflowRun(requirement.getId(), clock.instant()));
		auditService.record(run.getId(), null, AuditEventType.WORKFLOW_CREATED,
				"Workflow created for requirement " + requirement.getId());

		run.markPlanning();
		WorkflowGraphVersion graphVersion = graphService.createInitialGraph(run.getId());
		auditService.record(run.getId(), null, AuditEventType.GRAPH_CREATED,
				"Graph version " + graphVersion.getVersion() + " created");

		run.markReady(graphVersion.getVersion());
		return workflowRunRepository.save(run);
	}

	/**
	 * Starts a workflow and immediately dispatches the tasks that are eligible.
	 * Starting an already running workflow is a no-op.
	 *
	 * <p>The status change is committed by {@link WorkflowTransitionService} before
	 * dispatching, so worker threads always observe a RUNNING workflow.
	 */
	public WorkflowRun start(UUID workflowRunId) {
		WorkflowRun run = transitionService.startWorkflow(workflowRunId);
		engine.advance(workflowRunId);
		return run;
	}

	@Transactional(readOnly = true)
	public WorkflowRun getWorkflow(UUID workflowRunId) {
		return requireRun(workflowRunId);
	}

	@Transactional(readOnly = true)
	public String requirementText(UUID requirementId) {
		return requirementRepository.findById(requirementId)
				.map(Requirement::getText)
				.orElseThrow(() -> new WorkflowNotFoundException("Requirement " + requirementId + " not found"));
	}

	private WorkflowRun requireRun(UUID workflowRunId) {
		return workflowRunRepository.findById(workflowRunId)
				.orElseThrow(() -> new WorkflowNotFoundException("Workflow " + workflowRunId + " not found"));
	}
}
