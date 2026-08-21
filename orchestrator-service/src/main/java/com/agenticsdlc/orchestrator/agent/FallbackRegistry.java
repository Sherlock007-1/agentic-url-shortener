package com.agenticsdlc.orchestrator.agent;

import com.agenticsdlc.orchestrator.domain.AgentType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Registry of approved fallbacks, discovered from the Spring context.
 *
 * <p>Empty by default: with no approved fallback an exhausted primary agent leads
 * to a safe stop instead of a fabricated result.
 */
@Component
public class FallbackRegistry {

	private final Map<AgentType, AgentFallback> fallbacks = new EnumMap<>(AgentType.class);

	public FallbackRegistry(List<AgentFallback> discovered) {
		for (AgentFallback fallback : discovered) {
			AgentFallback previous = fallbacks.put(fallback.primaryType(), fallback);
			if (previous != null) {
				throw new IllegalStateException("Duplicate fallback registered for type " + fallback.primaryType());
			}
		}
	}

	public Optional<AgentFallback> find(AgentType type) {
		return Optional.ofNullable(fallbacks.get(type));
	}
}
