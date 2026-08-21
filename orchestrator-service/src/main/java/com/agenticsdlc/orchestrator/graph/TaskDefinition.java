package com.agenticsdlc.orchestrator.graph;

import com.agenticsdlc.orchestrator.domain.AgentType;
import java.util.List;

/**
 * Declarative description of one node in the workflow graph template.
 *
 * @param key        stable identifier of the task within the graph
 * @param name       human readable name
 * @param agentType  logical agent that executes the task
 * @param dependsOn  keys of the tasks that must complete first
 */
public record TaskDefinition(String key, String name, AgentType agentType, List<String> dependsOn) {

	public TaskDefinition {
		dependsOn = List.copyOf(dependsOn);
	}
}
