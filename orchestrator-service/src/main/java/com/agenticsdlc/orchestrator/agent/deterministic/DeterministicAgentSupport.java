package com.agenticsdlc.orchestrator.agent.deterministic;

import com.agenticsdlc.orchestrator.agent.Agent;
import com.agenticsdlc.orchestrator.agent.AgentContext;
import com.agenticsdlc.orchestrator.agent.AgentDecision;
import com.agenticsdlc.orchestrator.agent.AgentResult;
import com.agenticsdlc.orchestrator.agent.llm.LlmClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for the built-in deterministic agents.
 *
 * <p>Subclasses declare the instruction, the upstream context they require and any
 * decisions worth recording; producing the actual text is delegated to the
 * provider-neutral {@link LlmClient}.
 */
public abstract class DeterministicAgentSupport implements Agent {

	protected final LlmClient llmClient;

	protected DeterministicAgentSupport(LlmClient llmClient) {
		this.llmClient = llmClient;
	}

	/** Task keys whose output must be present before this agent can run. */
	protected abstract List<String> requiredUpstreamKeys();

	protected abstract String instruction();

	protected List<AgentDecision> decisionsFor(AgentContext context) {
		return List.of();
	}

	@Override
	public AgentResult execute(AgentContext context) {
		Map<String, String> prompt = new LinkedHashMap<>();
		prompt.put("requirement", context.requirement());
		for (String key : requiredUpstreamKeys()) {
			// Fails fast if the engine ever scheduled this task too early.
			prompt.put(key, context.requireUpstreamOutput(key));
		}
		String output = llmClient.complete(type().name(), instruction(), prompt);
		String summary = type().name() + " completed for task " + context.taskKey();
		return AgentResult.of(output, summary, decisionsFor(context));
	}
}
