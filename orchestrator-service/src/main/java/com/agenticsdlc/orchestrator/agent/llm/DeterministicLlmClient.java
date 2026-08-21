package com.agenticsdlc.orchestrator.agent.llm;

import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Local, deterministic {@link LlmClient} used for development and tests.
 *
 * <p>It performs no network calls and requires no API credentials: given the same
 * role, instruction and context it always produces the same text. This is a
 * demo/test implementation, not a simulation of a live model; a real provider
 * implementation replaces it by defining another {@code LlmClient} bean.
 */
@Component
public class DeterministicLlmClient implements LlmClient {

	@Override
	public String complete(String role, String instruction, Map<String, String> context) {
		StringBuilder text = new StringBuilder();
		text.append("[").append(role).append("] ").append(instruction);
		// TreeMap keeps the rendering order stable regardless of map implementation.
		for (Map.Entry<String, String> entry : new TreeMap<>(context).entrySet()) {
			text.append("\n- ").append(entry.getKey()).append(": ").append(abbreviate(entry.getValue()));
		}
		return text.toString();
	}

	private String abbreviate(String value) {
		if (value == null) {
			return "";
		}
		String normalised = value.replaceAll("\\s+", " ").trim();
		return normalised.length() <= 240 ? normalised : normalised.substring(0, 237) + "...";
	}
}
