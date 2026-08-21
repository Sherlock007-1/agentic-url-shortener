package com.agenticsdlc.orchestrator.agent.deterministic;

import com.agenticsdlc.orchestrator.agent.llm.LlmClient;
import com.agenticsdlc.orchestrator.domain.AgentType;
import java.util.List;
import org.springframework.stereotype.Component;

/** Interprets the raw requirement into structured intent. */
@Component
public class RequirementAnalysisAgent extends DeterministicAgentSupport {

	public RequirementAnalysisAgent(LlmClient llmClient) {
		super(llmClient);
	}

	@Override
	public AgentType type() {
		return AgentType.REQUIREMENT;
	}

	@Override
	protected List<String> requiredUpstreamKeys() {
		return List.of();
	}

	@Override
	protected String instruction() {
		return "Clarify scope, acceptance criteria and constraints of the requirement";
	}
}
