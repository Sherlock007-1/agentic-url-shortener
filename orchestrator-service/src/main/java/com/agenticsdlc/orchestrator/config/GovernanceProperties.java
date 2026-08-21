package com.agenticsdlc.orchestrator.config;

import com.agenticsdlc.orchestrator.domain.ApprovalGate;
import java.time.Duration;
import java.util.List;

/**
 * Governance, controlled-autonomy and recovery boundaries.
 *
 * <p>Every limit here is an explicit autonomy boundary: exceeding one never leads
 * to unbounded work, it leads to a controlled {@code SAFE_STOPPED} workflow with
 * audit evidence.
 *
 * @param approvalGates      gates that must be approved by a human; empty disables gating
 * @param maxTaskAttempts    maximum agent/task attempts per task (retries = attempts - 1).
 *                           Unrelated to the engine's optimistic-locking retries.
 * @param maxWorkflowDuration wall-clock budget for a run, measured from its start
 * @param taskTimeout        per-attempt agent execution timeout
 * @param workspaceRoot      root directory for {@code runs/{workflowId}/workspace|snapshots}
 */
public record GovernanceProperties(List<ApprovalGate> approvalGates, int maxTaskAttempts,
		Duration maxWorkflowDuration, Duration taskTimeout, String workspaceRoot) {

	public GovernanceProperties {
		approvalGates = approvalGates == null ? List.of() : List.copyOf(approvalGates);
		maxTaskAttempts = maxTaskAttempts <= 0 ? 3 : maxTaskAttempts;
		maxWorkflowDuration = maxWorkflowDuration == null ? Duration.ofMinutes(10) : maxWorkflowDuration;
		taskTimeout = taskTimeout == null ? Duration.ofMinutes(2) : taskTimeout;
		workspaceRoot = workspaceRoot == null || workspaceRoot.isBlank() ? "runs" : workspaceRoot;
	}

	public static GovernanceProperties defaults() {
		return new GovernanceProperties(List.of(), 0, null, null, null);
	}

	public boolean isGateEnabled(ApprovalGate gate) {
		return approvalGates.contains(gate);
	}
}
