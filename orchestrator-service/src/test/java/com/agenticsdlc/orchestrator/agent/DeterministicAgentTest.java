package com.agenticsdlc.orchestrator.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agenticsdlc.orchestrator.agent.deterministic.PlanningAgent;
import com.agenticsdlc.orchestrator.agent.deterministic.ValidationAgent;
import com.agenticsdlc.orchestrator.agent.llm.DeterministicLlmClient;
import com.agenticsdlc.orchestrator.domain.AgentType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeterministicAgentTest {

	private final DeterministicLlmClient llmClient = new DeterministicLlmClient();

	@Test
	void planningAgentUsesUpstreamContextAndRecordsADecision() {
		PlanningAgent agent = new PlanningAgent(llmClient);
		AgentContext context = new AgentContext(UUID.randomUUID(), UUID.randomUUID(), "planning", "Add analytics",
				Map.of("codebase-analysis", "affected modules: shortener"));

		AgentResult result = agent.execute(context);

		assertThat(agent.type()).isEqualTo(AgentType.PLANNING);
		assertThat(result.output()).contains("PLANNING").contains("affected modules: shortener").contains("Add analytics");
		assertThat(result.decisions()).singleElement()
				.satisfies(decision -> assertThat(decision.type()).isEqualTo("PLANNING"));
	}

	@Test
	void agentsAreDeterministicForTheSameContext() {
		PlanningAgent agent = new PlanningAgent(llmClient);
		AgentContext context = new AgentContext(UUID.randomUUID(), UUID.randomUUID(), "planning", "Add analytics",
				Map.of("codebase-analysis", "analysis"));

		assertThat(agent.execute(context).output()).isEqualTo(agent.execute(context).output());
	}

	@Test
	void validationAgentRefusesToRunWithoutAllThreeBranches() {
		ValidationAgent agent = new ValidationAgent(llmClient);
		AgentContext context = new AgentContext(UUID.randomUUID(), UUID.randomUUID(), "validation", "Add analytics",
				Map.of("tests", "tests output", "security", "security output"));

		assertThatThrownBy(() -> agent.execute(context))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("documentation");
	}
}
