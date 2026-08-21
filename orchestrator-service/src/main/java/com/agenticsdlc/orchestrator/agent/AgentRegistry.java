package com.agenticsdlc.orchestrator.agent;

import com.agenticsdlc.orchestrator.domain.AgentType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@link Agent} responsible for a logical role.
 *
 * <p>Agents are discovered from the Spring context, so replacing the deterministic
 * implementations with LLM-backed ones is a wiring change only.
 */
@Component
public class AgentRegistry {

	private final Map<AgentType, Agent> agents = new EnumMap<>(AgentType.class);

	public AgentRegistry(List<Agent> discoveredAgents) {
		for (Agent agent : discoveredAgents) {
			Agent previous = agents.put(agent.type(), agent);
			if (previous != null) {
				throw new IllegalStateException("Duplicate agent registered for type " + agent.type());
			}
		}
	}

	public Agent require(AgentType type) {
		Agent agent = agents.get(type);
		if (agent == null) {
			throw new IllegalStateException("No agent registered for type " + type);
		}
		return agent;
	}

	public boolean supports(AgentType type) {
		return agents.containsKey(type);
	}
}
