package com.agenticsdlc.orchestrator.agent.deterministic;

import static com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate.PLANNING;

import com.agenticsdlc.orchestrator.agent.AgentContext;
import com.agenticsdlc.orchestrator.agent.AgentDecision;
import com.agenticsdlc.orchestrator.agent.llm.LlmClient;
import com.agenticsdlc.orchestrator.domain.AgentType;
import java.util.List;
import org.springframework.stereotype.Component;

/** Derives the technical design from the plan and records architecture decisions. */
@Component
public class ArchitectureAgent extends DeterministicAgentSupport {

	public ArchitectureAgent(LlmClient llmClient) {
		super(llmClient);
	}

	@Override
	public AgentType type() {
		return AgentType.ARCHITECTURE;
	}

	@Override
	protected List<String> requiredUpstreamKeys() {
		return List.of(PLANNING);
	}

	@Override
	protected String instruction() {
		return "Define the layering, contracts and persistence impact of the plan";
	}

	@Override
	protected List<AgentDecision> decisionsFor(AgentContext context) {
		return List.of(new AgentDecision("ARCHITECTURE", "Layered service design",
				"Controller, service and repository layers keep the change reviewable and testable "
						+ "without introducing new infrastructure."));
	}
}
