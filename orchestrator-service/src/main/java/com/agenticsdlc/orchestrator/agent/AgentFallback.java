package com.agenticsdlc.orchestrator.agent;

import com.agenticsdlc.orchestrator.domain.AgentType;

/**
 * An explicitly approved, degraded alternative for a primary agent.
 *
 * <p>A fallback is only invoked after the primary agent exhausted its bounded
 * retries. Its result is never presented as equivalent to a successful primary
 * execution: the output is marked, a decision record is written and the audit
 * trail shows the degradation.
 */
public interface AgentFallback {

	/** The primary agent role this fallback may substitute. */
	AgentType primaryType();

	/** Short description of what the fallback does and why it is acceptable. */
	String description();

	AgentResult execute(AgentContext context);
}
