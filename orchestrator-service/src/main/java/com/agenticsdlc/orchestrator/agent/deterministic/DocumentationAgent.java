package com.agenticsdlc.orchestrator.agent.deterministic;

import static com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate.IMPLEMENTATION;

import com.agenticsdlc.orchestrator.agent.llm.LlmClient;
import com.agenticsdlc.orchestrator.domain.AgentType;
import java.util.List;
import org.springframework.stereotype.Component;

/** Parallel branch: documents the implemented behaviour. */
@Component
public class DocumentationAgent extends DeterministicAgentSupport {

	public DocumentationAgent(LlmClient llmClient) {
		super(llmClient);
	}

	@Override
	public AgentType type() {
		return AgentType.DOCUMENTATION;
	}

	@Override
	protected List<String> requiredUpstreamKeys() {
		return List.of(IMPLEMENTATION);
	}

	@Override
	protected String instruction() {
		return "Document the delivered behaviour, APIs and operational notes";
	}
}
