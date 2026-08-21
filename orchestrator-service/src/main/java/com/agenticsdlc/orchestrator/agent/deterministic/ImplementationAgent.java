package com.agenticsdlc.orchestrator.agent.deterministic;

import static com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate.ARCHITECTURE;

import com.agenticsdlc.orchestrator.agent.llm.LlmClient;
import com.agenticsdlc.orchestrator.domain.AgentType;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Produces the implementation proposal.
 *
 * <p>This branch deliberately does not mutate any source files; the agent only
 * describes the change so the orchestration core stays side-effect free.
 */
@Component
public class ImplementationAgent extends DeterministicAgentSupport {

	public ImplementationAgent(LlmClient llmClient) {
		super(llmClient);
	}

	@Override
	public AgentType type() {
		return AgentType.IMPLEMENTATION;
	}

	@Override
	protected List<String> requiredUpstreamKeys() {
		return List.of(ARCHITECTURE);
	}

	@Override
	protected String instruction() {
		return "Describe the concrete code changes implementing the approved design";
	}
}
