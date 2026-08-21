package com.agenticsdlc.orchestrator.governance;

import com.agenticsdlc.orchestrator.domain.ApprovalGate;
import com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate;
import java.util.Optional;

/**
 * Maps an approval gate onto the graph, keeping the gate enum free of graph
 * knowledge.
 *
 * <p>{@link ApprovalGate#FINAL} has no task key: it guards the transition to
 * COMPLETED after every task (including validation) finished.
 */
public final class ApprovalGates {

	private ApprovalGates() {
	}

	/** The task that may not start before this gate is approved, if any. */
	public static Optional<String> blockedTaskKey(ApprovalGate gate) {
		return gate == ApprovalGate.PRE_IMPLEMENTATION
				? Optional.of(SdlcWorkflowGraphTemplate.IMPLEMENTATION)
				: Optional.empty();
	}

	/** The gate that blocks the given task key, if any. */
	public static Optional<ApprovalGate> gateForTask(String taskKey) {
		return SdlcWorkflowGraphTemplate.IMPLEMENTATION.equals(taskKey)
				? Optional.of(ApprovalGate.PRE_IMPLEMENTATION)
				: Optional.empty();
	}

	public static String describe(ApprovalGate gate) {
		return switch (gate) {
			case PRE_IMPLEMENTATION -> "Architecture is complete; approve before implementation starts";
			case FINAL -> "Validation is complete; approve before the workflow is marked COMPLETED";
		};
	}
}
