package com.agenticsdlc.orchestrator.agent.llm;

import java.util.Map;

/**
 * Provider-neutral completion abstraction.
 *
 * <p>Agents depend on this interface only, so a real provider (OpenAI, Anthropic,
 * Bedrock, ...) can be introduced later as an additional implementation without
 * touching the orchestration engine, the graph or the agents' contracts.
 */
public interface LlmClient {

	/**
	 * @param role        logical agent role, e.g. {@code PLANNING}
	 * @param instruction what the agent is asked to produce
	 * @param context     named context fragments coming from upstream stages
	 * @return the completion text
	 */
	String complete(String role, String instruction, Map<String, String> context);
}
