package com.agenticsdlc.orchestrator.agent.deterministic;

import static com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate.DOCUMENTATION;
import static com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate.SECURITY;
import static com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate.TESTS;

import com.agenticsdlc.orchestrator.agent.llm.LlmClient;
import com.agenticsdlc.orchestrator.domain.AgentType;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Join node: consolidates the three parallel branches.
 *
 * <p>Requiring all three upstream outputs makes an incorrect early dispatch fail
 * loudly instead of silently producing a partial result.
 */
@Component
public class ValidationAgent extends DeterministicAgentSupport {

	public ValidationAgent(LlmClient llmClient) {
		super(llmClient);
	}

	@Override
	public AgentType type() {
		return AgentType.VALIDATION;
	}

	@Override
	protected List<String> requiredUpstreamKeys() {
		return List.of(TESTS, SECURITY, DOCUMENTATION);
	}

	@Override
	protected String instruction() {
		return "Consolidate test, security and documentation outcomes into a release verdict";
	}
}
