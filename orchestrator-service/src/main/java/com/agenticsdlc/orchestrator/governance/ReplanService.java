package com.agenticsdlc.orchestrator.governance;

import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.domain.ClarificationRequest;
import com.agenticsdlc.orchestrator.domain.Decision;
import com.agenticsdlc.orchestrator.domain.Requirement;
import com.agenticsdlc.orchestrator.domain.WorkflowGraphVersion;
import com.agenticsdlc.orchestrator.domain.WorkflowReplan;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.engine.AuditService;
import com.agenticsdlc.orchestrator.engine.IllegalWorkflowStateException;
import com.agenticsdlc.orchestrator.engine.WorkflowGraphService;
import com.agenticsdlc.orchestrator.engine.WorkflowNotFoundException;
import com.agenticsdlc.orchestrator.repository.ClarificationRequestRepository;
import com.agenticsdlc.orchestrator.repository.DecisionRepository;
import com.agenticsdlc.orchestrator.repository.RequirementRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowGraphVersionRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowReplanRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dynamic replanning.
 *
 * <p>A replan derives a <em>new</em> graph version from the SDLC template and
 * points the workflow at it. Version 1 (and every task, decision and audit event
 * that belongs to it) stays persisted and queryable - this is versioning, not
 * mutation. There is deliberately no graph-diff engine: a deterministic
 * regeneration plus recorded lineage is enough to explain what changed and why.
 *
 * <p>After a replan the run is READY again, so a human still has to start it and
 * the approval gates of the new version must be approved again.
 */
@Service
public class ReplanService {

	private final WorkflowRunRepository workflowRunRepository;
	private final WorkflowGraphVersionRepository graphVersionRepository;
	private final WorkflowReplanRepository replanRepository;
	private final RequirementRepository requirementRepository;
	private final ClarificationRequestRepository clarificationRepository;
	private final DecisionRepository decisionRepository;
	private final WorkflowGraphService graphService;
	private final AuditService auditService;
	private final Clock clock;

	public ReplanService(WorkflowRunRepository workflowRunRepository,
			WorkflowGraphVersionRepository graphVersionRepository, WorkflowReplanRepository replanRepository,
			RequirementRepository requirementRepository, ClarificationRequestRepository clarificationRepository,
			DecisionRepository decisionRepository, WorkflowGraphService graphService, AuditService auditService,
			Clock clock) {
		this.workflowRunRepository = workflowRunRepository;
		this.graphVersionRepository = graphVersionRepository;
		this.replanRepository = replanRepository;
		this.requirementRepository = requirementRepository;
		this.clarificationRepository = clarificationRepository;
		this.decisionRepository = decisionRepository;
		this.graphService = graphService;
		this.auditService = auditService;
		this.clock = clock;
	}

	/**
	 * Creates the next graph version for a workflow.
	 *
	 * @param changedRequirement clarified/changed requirement text, may be null to keep the current one
	 * @param reason             why replanning happened (persisted lineage)
	 * @param clarificationId    the answered clarification that triggered it, may be null
	 */
	@Transactional
	public WorkflowReplan replan(UUID workflowRunId, String changedRequirement, String reason, UUID clarificationId) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("A replan reason is required for lineage");
		}
		WorkflowRun run = requireRun(workflowRunId);
		if (run.getCurrentGraphVersion() <= 0) {
			throw new IllegalWorkflowStateException(
					"Workflow " + workflowRunId + " has no graph version to replan from");
		}
		Requirement requirement = requirementRepository.findById(run.getRequirementId())
				.orElseThrow(() -> new WorkflowNotFoundException("Requirement " + run.getRequirementId()
						+ " not found"));
		if (clarificationId != null) {
			ClarificationRequest clarification = clarificationRepository.findById(clarificationId)
					.orElseThrow(() -> new WorkflowNotFoundException(
							"Clarification " + clarificationId + " not found"));
			if (!clarification.getWorkflowRunId().equals(workflowRunId)) {
				throw new WorkflowNotFoundException(
						"Clarification " + clarificationId + " does not belong to workflow " + workflowRunId);
			}
		}

		int fromVersion = run.getCurrentGraphVersion();
		int toVersion = nextVersion(workflowRunId);
		String previousRequirement = requirement.getText();
		String newRequirement = changedRequirement == null || changedRequirement.isBlank() ? previousRequirement
				: changedRequirement;
		Instant now = clock.instant();

		run.markReplanning();
		workflowRunRepository.save(run);
		auditService.record(workflowRunId, null, AuditEventType.REPLAN_STARTED,
				"Replanning from graph version " + fromVersion + " to " + toVersion, reason);

		if (!newRequirement.equals(previousRequirement)) {
			requirement.updateText(newRequirement);
			requirementRepository.save(requirement);
		}

		WorkflowGraphVersion created = graphService.createGraph(workflowRunId, toVersion,
				"Replan v" + toVersion + ": " + truncate(reason));
		auditService.record(workflowRunId, null, AuditEventType.GRAPH_CREATED,
				"Graph version " + created.getVersion() + " created by replanning", reason);

		WorkflowReplan lineage = replanRepository.save(new WorkflowReplan(workflowRunId, fromVersion, toVersion, reason,
				previousRequirement, newRequirement, clarificationId, now));
		decisionRepository.save(new Decision(workflowRunId, null, "REPLAN",
				"Graph version " + toVersion + " created from version " + fromVersion,
				reason + " Previous requirement: \"" + previousRequirement + "\". New requirement: \"" + newRequirement
						+ "\". Version " + fromVersion + " remains persisted for lineage.",
				now));

		// The new version needs a human start and fresh approvals; autonomy is not
		// inherited from the previous version.
		run.markReady(toVersion);
		workflowRunRepository.save(run);
		auditService.record(workflowRunId, null, AuditEventType.REPLAN_COMPLETED,
				"Workflow now runs graph version " + toVersion + " (version " + fromVersion + " preserved)", reason);
		return lineage;
	}

	@Transactional(readOnly = true)
	public List<WorkflowReplan> history(UUID workflowRunId) {
		requireRun(workflowRunId);
		return replanRepository.findByWorkflowRunIdOrderByCreatedAtAsc(workflowRunId);
	}

	@Transactional(readOnly = true)
	public List<WorkflowGraphVersion> graphVersions(UUID workflowRunId) {
		requireRun(workflowRunId);
		return graphVersionRepository.findByWorkflowRunIdOrderByVersionAsc(workflowRunId);
	}

	private int nextVersion(UUID workflowRunId) {
		return graphVersionRepository.findByWorkflowRunIdOrderByVersionAsc(workflowRunId).stream()
				.mapToInt(WorkflowGraphVersion::getVersion)
				.max()
				.orElse(0) + 1;
	}

	private String truncate(String reason) {
		return reason.length() <= 180 ? reason : reason.substring(0, 177) + "...";
	}

	private WorkflowRun requireRun(UUID workflowRunId) {
		return workflowRunRepository.findById(workflowRunId)
				.orElseThrow(() -> new WorkflowNotFoundException("Workflow " + workflowRunId + " not found"));
	}
}
