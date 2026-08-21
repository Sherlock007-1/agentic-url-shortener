package com.agenticsdlc.orchestrator.agent;

import java.util.List;

/**
 * Outcome of an agent invocation.
 *
 * @param output    text/JSON output handed to downstream tasks as context
 * @param summary   short human readable summary for the audit trail
 * @param decisions decisions to persist for lineage
 */
public record AgentResult(String output, String summary, List<AgentDecision> decisions) {

	public AgentResult {
		decisions = List.copyOf(decisions);
	}

	public static AgentResult of(String output, String summary) {
		return new AgentResult(output, summary, List.of());
	}

	public static AgentResult of(String output, String summary, List<AgentDecision> decisions) {
		return new AgentResult(output, summary, decisions);
	}
}
