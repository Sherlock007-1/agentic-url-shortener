package com.agenticsdlc.orchestrator.agent.deterministic;

import static com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate.REQUIREMENT_ANALYSIS;

import com.agenticsdlc.orchestrator.agent.llm.LlmClient;
import com.agenticsdlc.orchestrator.domain.AgentType;
import java.util.List;
import org.springframework.stereotype.Component;

/** Inspects the existing codebase in the light of the analysed requirement. */
@Component
public class CodebaseAnalysisAgent extends DeterministicAgentSupport {

	public CodebaseAnalysisAgent(LlmClient llmClient) {
		super(llmClient);
	}

	@Override
	public AgentType type() {
		return AgentType.CODEBASE_ANALYSIS;
	}

	@Override
	protected List<String> requiredUpstreamKeys() {
		return List.of(REQUIREMENT_ANALYSIS);
	}

	@Override
	protected String instruction() {
		return "Identify affected modules, entry points and integration risks";
	}
}
