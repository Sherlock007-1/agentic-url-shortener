package com.agenticsdlc.orchestrator.governance;

import com.agenticsdlc.orchestrator.domain.AuditEventType;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Applies {@link ChangePolicyGuard} inside a workflow and makes rejections
 * auditable.
 *
 * <p>A rejected mutation is not "handled and continued": the action is prevented,
 * a {@code POLICY_REJECTED} event is written and the run is safe-stopped, because
 * an agent that tried to leave its approved boundary should not keep going.
 */
@Service
public class PolicyGuardService {

	private final WorkspaceService workspaceService;
	private final SafeStopService safeStopService;

	public PolicyGuardService(WorkspaceService workspaceService, SafeStopService safeStopService) {
		this.workspaceService = workspaceService;
		this.safeStopService = safeStopService;
	}

	/**
	 * Authorises a mutation inside the workspace of a run.
	 *
	 * <p>Deliberately not transactional: the rejection is reported by throwing, and
	 * the evidence is committed separately so the exception cannot roll it back.
	 *
	 * @return the normalised, approved absolute path
	 * @throws PolicyViolationException when the policy rejects the mutation
	 */
	public Path requireMutable(UUID workflowRunId, UUID taskId, Path target) {
		ChangePolicyGuard guard = workspaceService.guardFor(workflowRunId);
		PolicyDecision decision = guard.evaluateMutation(target);
		if (decision.allowed()) {
			return guard.requireMutable(target);
		}
		safeStopService.recordAndSafeStop(workflowRunId, taskId, AuditEventType.POLICY_REJECTED,
				"Change policy rejected a mutation", decision.reason(),
				"Change policy violation: " + decision.reason());
		throw new PolicyViolationException(decision.reason());
	}

	/** Non-mutating evaluation, e.g. for a dry run or an API pre-check. */
	public PolicyDecision evaluate(UUID workflowRunId, Path target) {
		return workspaceService.guardFor(workflowRunId).evaluateMutation(target);
	}
}