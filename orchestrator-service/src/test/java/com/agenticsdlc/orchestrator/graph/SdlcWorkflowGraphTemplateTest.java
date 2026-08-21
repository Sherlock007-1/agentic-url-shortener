package com.agenticsdlc.orchestrator.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.agenticsdlc.orchestrator.domain.AgentType;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SdlcWorkflowGraphTemplateTest {

	private final Map<String, TaskDefinition> byKey = SdlcWorkflowGraphTemplate.tasks().stream()
			.collect(Collectors.toMap(TaskDefinition::key, Function.identity()));

	@Test
	void definesTheNineSdlcStages() {
		assertThat(byKey.keySet()).containsExactlyInAnyOrder(
				SdlcWorkflowGraphTemplate.REQUIREMENT_ANALYSIS,
				SdlcWorkflowGraphTemplate.CODEBASE_ANALYSIS,
				SdlcWorkflowGraphTemplate.PLANNING,
				SdlcWorkflowGraphTemplate.ARCHITECTURE,
				SdlcWorkflowGraphTemplate.IMPLEMENTATION,
				SdlcWorkflowGraphTemplate.TESTS,
				SdlcWorkflowGraphTemplate.SECURITY,
				SdlcWorkflowGraphTemplate.DOCUMENTATION,
				SdlcWorkflowGraphTemplate.VALIDATION);
	}

	@Test
	void modelsTheSequentialSpine() {
		assertThat(byKey.get(SdlcWorkflowGraphTemplate.REQUIREMENT_ANALYSIS).dependsOn()).isEmpty();
		assertThat(byKey.get(SdlcWorkflowGraphTemplate.CODEBASE_ANALYSIS).dependsOn())
				.containsExactly(SdlcWorkflowGraphTemplate.REQUIREMENT_ANALYSIS);
		assertThat(byKey.get(SdlcWorkflowGraphTemplate.PLANNING).dependsOn())
				.containsExactly(SdlcWorkflowGraphTemplate.CODEBASE_ANALYSIS);
		assertThat(byKey.get(SdlcWorkflowGraphTemplate.ARCHITECTURE).dependsOn())
				.containsExactly(SdlcWorkflowGraphTemplate.PLANNING);
		assertThat(byKey.get(SdlcWorkflowGraphTemplate.IMPLEMENTATION).dependsOn())
				.containsExactly(SdlcWorkflowGraphTemplate.ARCHITECTURE);
	}

	@Test
	void tests_security_and_documentation_areIndependentSiblings() {
		for (String key : List.of(SdlcWorkflowGraphTemplate.TESTS, SdlcWorkflowGraphTemplate.SECURITY,
				SdlcWorkflowGraphTemplate.DOCUMENTATION)) {
			assertThat(byKey.get(key).dependsOn())
					.as("%s depends only on implementation", key)
					.containsExactly(SdlcWorkflowGraphTemplate.IMPLEMENTATION);
		}
	}

	@Test
	void validationJoinsAllThreeBranches() {
		assertThat(byKey.get(SdlcWorkflowGraphTemplate.VALIDATION).dependsOn())
				.containsExactlyInAnyOrder(SdlcWorkflowGraphTemplate.TESTS, SdlcWorkflowGraphTemplate.SECURITY,
						SdlcWorkflowGraphTemplate.DOCUMENTATION);
	}

	@Test
	void eachStageIsBoundToItsOwnAgentType() {
		assertThat(byKey.values().stream().map(TaskDefinition::agentType).collect(Collectors.toSet()))
				.hasSize(AgentType.values().length);
	}

	@Test
	void declarationOrderIsTopological() {
		Set<String> seen = new HashSet<>();
		for (TaskDefinition definition : SdlcWorkflowGraphTemplate.tasks()) {
			assertThat(seen).as("dependencies of %s declared first", definition.key())
					.containsAll(definition.dependsOn());
			seen.add(definition.key());
		}
	}
}
