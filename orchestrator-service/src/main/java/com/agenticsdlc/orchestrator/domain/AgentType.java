package com.agenticsdlc.orchestrator.domain;

/**
 * Logical agent responsible for a task.
 *
 * <p>The type is a role, not a vendor: the same role can later be served by an
 * LLM-backed implementation without changing the graph or the persisted data.
 */
public enum AgentType {
	REQUIREMENT,
	CODEBASE_ANALYSIS,
	PLANNING,
	ARCHITECTURE,
	IMPLEMENTATION,
	TEST,
	SECURITY_RISK,
	DOCUMENTATION,
	VALIDATION
}
