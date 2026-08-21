package com.agenticsdlc.orchestrator.domain;

/**
 * Which agent produced an attempt.
 *
 * <p>Deliberately distinct from the internal optimistic-locking retry of the
 * engine: only agent/task attempts are recorded here.
 */
public enum AttemptKind {

	/** The agent registered for the task's {@link AgentType}. */
	PRIMARY,

	/** The explicitly approved fallback agent, used only after retries are exhausted. */
	FALLBACK
}
