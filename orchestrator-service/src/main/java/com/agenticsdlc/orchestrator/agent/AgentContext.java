package com.agenticsdlc.orchestrator.agent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything an agent is allowed to see: the requirement plus the outputs
 * produced by upstream tasks (cross-stage context).
 *
 * @param workflowRunId  owning workflow
 * @param taskId         task being executed
 * @param taskKey        stable task key from the graph
 * @param requirement    original requirement text
 * @param upstreamOutputs outputs of completed predecessor tasks, keyed by task key
 */
public record AgentContext(UUID workflowRunId, UUID taskId, String taskKey, String requirement,
		Map<String, String> upstreamOutputs) {

	public AgentContext {
		upstreamOutputs = Map.copyOf(upstreamOutputs);
	}

	public Optional<String> upstreamOutput(String taskKey) {
		return Optional.ofNullable(upstreamOutputs.get(taskKey));
	}

	public String requireUpstreamOutput(String taskKey) {
		return upstreamOutput(taskKey).orElseThrow(
				() -> new IllegalStateException("Missing upstream context from task '" + taskKey + "'"));
	}
}
