package com.agenticsdlc.orchestrator.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agenticsdlc.orchestrator.domain.AgentType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentRegistryTest {

	private static Agent agentOf(AgentType type) {
		return new Agent() {
			@Override
			public AgentType type() {
				return type;
			}

			@Override
			public AgentResult execute(AgentContext context) {
				return AgentResult.of("output", "summary");
			}
		};
	}

	@Test
	void resolvesAgentByLogicalType() {
		AgentRegistry registry = new AgentRegistry(List.of(agentOf(AgentType.PLANNING), agentOf(AgentType.TEST)));

		assertThat(registry.require(AgentType.PLANNING).type()).isEqualTo(AgentType.PLANNING);
		assertThat(registry.supports(AgentType.TEST)).isTrue();
		assertThat(registry.supports(AgentType.VALIDATION)).isFalse();
	}

	@Test
	void failsFastOnUnknownType() {
		AgentRegistry registry = new AgentRegistry(List.of(agentOf(AgentType.PLANNING)));

		assertThatThrownBy(() -> registry.require(AgentType.VALIDATION))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("VALIDATION");
	}

	@Test
	void rejectsTwoAgentsForTheSameType() {
		assertThatThrownBy(() -> new AgentRegistry(List.of(agentOf(AgentType.PLANNING), agentOf(AgentType.PLANNING))))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Duplicate agent");
	}

	@Test
	void contextExposesUpstreamOutputsAndFailsWhenMissing() {
		AgentContext context = new AgentContext(UUID.randomUUID(), UUID.randomUUID(), "planning", "requirement",
				Map.of("codebase-analysis", "analysis output"));

		assertThat(context.requireUpstreamOutput("codebase-analysis")).isEqualTo("analysis output");
		assertThat(context.upstreamOutput("missing")).isEmpty();
		assertThatThrownBy(() -> context.requireUpstreamOutput("missing"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Missing upstream context");
	}
}
