package com.agenticsdlc.orchestrator.agent.deterministic;

import static com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate.IMPLEMENTATION;

import com.agenticsdlc.orchestrator.agent.llm.LlmClient;
import com.agenticsdlc.orchestrator.domain.AgentType;
import java.util.List;
import org.springframework.stereotype.Component;

/** Parallel branch: derives the test strategy for the implementation. */
@Component
public class TestAgent extends DeterministicAgentSupport {

	public TestAgent(LlmClient llmClient) {
		super(llmClient);
	}

	@Override
	public AgentType type() {
		return AgentType.TEST;
	}

	@Override
	protected List<String> requiredUpstreamKeys() {
		return List.of(IMPLEMENTATION);
	}

	@Override
	protected String instruction() {
		return "Define unit and integration tests covering the implemented behaviour";
	}
}
