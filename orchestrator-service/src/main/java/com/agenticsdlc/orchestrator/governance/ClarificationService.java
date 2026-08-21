package com.agenticsdlc.orchestrator.governance;

import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.domain.ClarificationRequest;
import com.agenticsdlc.orchestrator.domain.ClarificationStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.engine.AuditService;
import com.agenticsdlc.orchestrator.engine.IllegalWorkflowStateException;
import com.agenticsdlc.orchestrator.engine.WorkflowNotFoundException;
import com.agenticsdlc.orchestrator.repository.ClarificationRequestRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clarification gate: the orchestrator can stop and ask instead of guessing.
 *
 * <p>Generic mechanism only. The question text is supplied by the caller/agent, so
 * the later "ambiguous requirement" scenario can use this without changing the
 * engine. Answering un-parks the workflow; the caller decides whether the answer
 * should also trigger a replan.
 */
@Service
public class ClarificationService {

	private final ClarificationRequestRepository clarificationRepository;
	private final WorkflowRunRepository workflowRunRepository;
	private final AuditService auditService;
	private final Clock clock;

	public ClarificationService(ClarificationRequestRepository clarificationRepository,
			WorkflowRunRepository workflowRunRepository, AuditService auditService, Clock clock) {
		this.clarificationRepository = clarificationRepository;
		this.workflowRunRepository = workflowRunRepository;
		this.auditService = auditService;
		this.clock = clock;
	}

	/** Parks the workflow in AWAITING_CLARIFICATION with a persisted question. */
	@Transactional
	public ClarificationRequest ask(UUID workflowRunId, UUID taskId, String question) {
		if (question == null || question.isBlank()) {
			throw new IllegalArgumentException("Clarification question must not be blank");
		}
		WorkflowRun run = requireRun(workflowRunId);
		if (run.getStatus().isTerminal()) {
			throw new IllegalWorkflowStateException(
					"Workflow " + workflowRunId + " is " + run.getStatus() + " and cannot ask for clarification");
		}
		ClarificationRequest request = clarificationRepository
				.save(new ClarificationRequest(workflowRunId, taskId, question, clock.instant()));

		run.markAwaitingClarification();
		workflowRunRepository.save(run);
		auditService.record(workflowRunId, taskId, AuditEventType.CLARIFICATION_REQUESTED,
				"Clarification requested", question);
		return request;
	}

	@Transactional(readOnly = true)
	public List<ClarificationRequest> clarifications(UUID workflowRunId) {
		requireRun(workflowRunId);
		return clarificationRepository.findByWorkflowRunIdOrderByRequestedAtAsc(workflowRunId);
	}

	/**
	 * Persists the human answer and, when no question is left open, returns the
	 * workflow to RUNNING so it can continue (or be replanned).
	 */
	@Transactional
	public ClarificationRequest answer(UUID workflowRunId, UUID clarificationId, String answer, String answeredBy) {
		if (answer == null || answer.isBlank()) {
			throw new IllegalArgumentException("Clarification answer must not be blank");
		}
		ClarificationRequest request = clarificationRepository.findById(clarificationId)
				.orElseThrow(() -> new WorkflowNotFoundException("Clarification " + clarificationId + " not found"));
		if (!request.getWorkflowRunId().equals(workflowRunId)) {
			throw new WorkflowNotFoundException(
					"Clarification " + clarificationId + " does not belong to workflow " + workflowRunId);
		}
		if (request.getStatus().isResolved()) {
			throw new IllegalWorkflowStateException("Clarification " + clarificationId + " is already answered");
		}

		Instant now = clock.instant();
		request.answer(answer, answeredBy, now);
		clarificationRepository.save(request);
		auditService.record(workflowRunId, request.getTaskId(), AuditEventType.CLARIFICATION_ANSWERED,
				"Clarification answered by " + (answeredBy == null || answeredBy.isBlank() ? "unspecified operator"
						: answeredBy),
				answer);

		WorkflowRun run = requireRun(workflowRunId);
		boolean stillPending = !clarificationRepository
				.findByWorkflowRunIdAndStatus(workflowRunId, ClarificationStatus.PENDING).isEmpty();
		if (!stillPending && run.getStatus() == WorkflowStatus.AWAITING_CLARIFICATION) {
			run.markRunning(now);
			workflowRunRepository.save(run);
		}
		return request;
	}

	private WorkflowRun requireRun(UUID workflowRunId) {
		return workflowRunRepository.findById(workflowRunId)
				.orElseThrow(() -> new WorkflowNotFoundException("Workflow " + workflowRunId + " not found"));
	}
}
