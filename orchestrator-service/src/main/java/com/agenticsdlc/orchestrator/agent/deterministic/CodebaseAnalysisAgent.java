package com.agenticsdlc.orchestrator.agent.deterministic;

import static com.agenticsdlc.orchestrator.graph.SdlcWorkflowGraphTemplate.REQUIREMENT_ANALYSIS;

import com.agenticsdlc.orchestrator.agent.AgentContext;
import com.agenticsdlc.orchestrator.agent.AgentDecision;
import com.agenticsdlc.orchestrator.agent.llm.LlmClient;
import com.agenticsdlc.orchestrator.domain.AgentType;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Inspects the existing codebase in the light of the analysed requirement.
 *
 * <p>When the requirement asks for behaviour the target service already provides,
 * that is recorded as a decision instead of being implemented again - see
 * {@link KnownCapabilities}.
 */
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

	@Override
	protected List<AgentDecision> decisionsFor(AgentContext context) {
		return KnownCapabilities.match(context.requirement())
				.map(capability -> List.of(KnownCapabilities.alreadyImplementedDecision(capability)))
				.orElseGet(List::of);
	}
}