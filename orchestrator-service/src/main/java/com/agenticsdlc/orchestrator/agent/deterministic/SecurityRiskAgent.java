package com.agenticsdlc.orchestrator.agent.deterministic;

import static com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate.IMPLEMENTATION;

import com.agenticsdlc.orchestrator.agent.llm.LlmClient;
import com.agenticsdlc.orchestrator.domain.AgentType;
import java.util.List;
import org.springframework.stereotype.Component;

/** Parallel branch: reviews the implementation for security and risk concerns. */
@Component
public class SecurityRiskAgent extends DeterministicAgentSupport {

	public SecurityRiskAgent(LlmClient llmClient) {
		super(llmClient);
	}

	@Override
	public AgentType type() {
		return AgentType.SECURITY_RISK;
	}

	@Override
	protected List<String> requiredUpstreamKeys() {
		return List.of(IMPLEMENTATION);
	}

	@Override
	protected String instruction() {
		return "Review the change for input validation, data exposure and dependency risk";
	}
}
