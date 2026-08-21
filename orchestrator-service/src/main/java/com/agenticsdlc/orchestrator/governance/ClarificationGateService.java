package com.agenticsdlc.orchestrator.governance;

import com.agenticsdlc.orchestrator.domain.ClarificationRequest;
import com.agenticsdlc.orchestrator.domain.Decision;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowTask;
import com.agenticsdlc.orchestrator.governance.AmbiguityDetector.Ambiguity;
import com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate;
import com.agenticsdlc.orchestrator.repository.ClarificationRequestRepository;
import com.agenticsdlc.orchestrator.repository.DecisionRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clarification gate for ambiguous requirements.
 *
 * <p>Mirrors {@link ApprovalService#requireGate}: the scheduler asks this gate
 * before a task may run, and a {@code false} answer means "eligible by data, but
 * autonomy stops here". The gate never invents an interpretation - it parks the
 * workflow in {@code AWAITING_CLARIFICATION} with a persisted question and a
 * persisted rationale, and waits for a human.
 *
 * <p>It guards {@link SdlcWorkflowGraphTemplate#CODEBASE_ANALYSIS}, i.e. the moment
 * right after requirement analysis has run: the ambiguity is reported once the
 * requirement has actually been looked at, and before any analysis, planning or
 * implementation work is based on a guess.
 *
 * <p>Triggering is narrow by design (see {@link AmbiguityDetector}); the gate is a
 * no-op for every requirement that does not match a known ambiguity pattern.
 */
@Service
public class ClarificationGateService {

	private static final Logger log = LoggerFactory.getLogger(ClarificationGateService.class);

	/** The task that must not start while the requirement is still ambiguous. */
	public static final String GUARDED_TASK_KEY = SdlcWorkflowGraphTemplate.CODEBASE_ANALYSIS;

	private final AmbiguityDetector ambiguityDetector;
	private final ClarificationService clarificationService;
	private final ClarificationRequestRepository clarificationRepository;
	private final DecisionRepository decisionRepository;
	private final Clock clock;

	public ClarificationGateService(AmbiguityDetector ambiguityDetector, ClarificationService clarificationService,
			ClarificationRequestRepository clarificationRepository, DecisionRepository decisionRepository, Clock clock) {
		this.ambiguityDetector = ambiguityDetector;
		this.clarificationService = clarificationService;
		this.clarificationRepository = clarificationRepository;
		this.decisionRepository = decisionRepository;
		this.clock = clock;
	}

	/** True when this task key is subject to the ambiguity gate at all. */
	public boolean guards(String taskKey) {
		return GUARDED_TASK_KEY.equals(taskKey);
	}

	/**
	 * Checks whether the requirement is actionable before autonomous work continues.
	 *
	 * <p>The question is asked at most once per workflow: once a human has answered
	 * it, the workflow continues under the human decision even if the stored
	 * requirement text was left unchanged. That keeps the gate a stop-and-ask, never
	 * an endless loop.
	 *
	 * @return {@code true} when execution may continue
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean requireActionableRequirement(WorkflowRun run, String requirementText, WorkflowTask blockedTask) {
		Optional<Ambiguity> detected = ambiguityDetector.detect(requirementText);
		if (detected.isEmpty()) {
			return true;
		}
		Ambiguity ambiguity = detected.get();

		List<ClarificationRequest> asked = clarificationRepository.findByWorkflowRunIdOrderByRequestedAtAsc(run.getId())
				.stream()
				.filter(request -> ambiguity.question().equals(request.getQuestion()))
				.toList();
		if (asked.stream().anyMatch(request -> request.getStatus().isResolved())) {
			// A human already answered this exact question for this run.
			return true;
		}
		if (!asked.isEmpty()) {
			// Still pending: stay parked without asking the same question twice.
			return false;
		}

		log.info("Requirement of workflow {} matched ambiguity '{}'; stopping to ask instead of guessing",
				run.getId(), ambiguity.key());
		decisionRepository.save(new Decision(run.getId(), blockedTask == null ? null : blockedTask.getId(),
				"CLARIFICATION", "Ambiguous requirement detected: " + ambiguity.key(),
				ambiguity.rationale() + " The workflow was parked before '" + GUARDED_TASK_KEY
						+ "' rather than choosing an interpretation autonomously.",
				clock.instant()));
		clarificationService.ask(run.getId(), blockedTask == null ? null : blockedTask.getId(), ambiguity.question());
		return false;
	}
}
