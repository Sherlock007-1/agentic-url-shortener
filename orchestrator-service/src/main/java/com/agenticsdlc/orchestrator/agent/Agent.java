package com.agenticsdlc.orchestrator.agent;

import com.agenticsdlc.orchestrator.domain.AgentType;
import java.util.List;

/**
 * A unit of agentic work bound to a logical role.
 *
 * <p>Implementations must be side-effect free with respect to orchestration
 * state: persistence of status, output, decisions and audit is the engine's job.
 */
public interface Agent {

	AgentType type();

	AgentResult execute(AgentContext context);

	/** Decisions this agent contributed, derived from its result. */
	default List<AgentDecision> decisions(AgentResult result) {
		return result.decisions();
	}
}
