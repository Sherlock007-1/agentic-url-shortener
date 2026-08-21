package com.agenticsdlc.orchestrator.agent;

/**
 * A decision an agent wants recorded for lineage purposes.
 *
 * @param type      decision category, e.g. {@code PLANNING} or {@code ARCHITECTURE}
 * @param title     short summary
 * @param rationale why the decision was taken
 */
public record AgentDecision(String type, String title, String rationale) {
}
