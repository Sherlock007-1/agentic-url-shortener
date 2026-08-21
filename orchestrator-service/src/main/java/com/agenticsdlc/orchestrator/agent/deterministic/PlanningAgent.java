package com.agenticsdlc.orchestrator.agent.deterministic;

import static com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate.CODEBASE_ANALYSIS;

import com.agenticsdlc.orchestrator.agent.AgentContext;
import com.agenticsdlc.orchestrator.agent.AgentDecision;
import com.agenticsdlc.orchestrator.agent.llm.LlmClient;
import com.agenticsdlc.orchestrator.domain.AgentType;
import java.util.List;
import org.springframework.stereotype.Component;

/** Turns the analysis into an implementation plan and records planning decisions. */
@Component
public class PlanningAgent extends DeterministicAgentSupport {

	public PlanningAgent(LlmClient llmClient) {
		super(llmClient);
	}

	@Override
	public AgentType type() {
		return AgentType.PLANNING;
	}

	@Override
	protected List<String> requiredUpstreamKeys() {
		return List.of(CODEBASE_ANALYSIS);
	}

	@Override
	protected String instruction() {
		return "Break the requirement into ordered, verifiable implementation steps";
	}

	@Override
	protected List<AgentDecision> decisionsFor(AgentContext context) {
		return List.of(new AgentDecision("PLANNING", "Incremental delivery plan",
				"The requirement is decomposed into sequential steps so each stage can be validated "
						+ "before the next one starts."));
	}
}
