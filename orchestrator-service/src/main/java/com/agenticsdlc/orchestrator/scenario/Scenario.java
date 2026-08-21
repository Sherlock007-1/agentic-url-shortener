package com.agenticsdlc.orchestrator.scenario;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A reproducible assessment scenario.
 *
 * <p>Only the fields needed to start and explain a scenario live here. The full,
 * human-readable definition (expected behaviour, affected components, validation
 * expectations, intentional boundaries) is kept in {@code scenarios/{key}.json} at
 * the repository root so it stays reviewable without running anything - this record
 * points at that file instead of duplicating it.
 *
 * @param key         stable identifier used in the API
 * @param name        human readable title
 * @param type        greenfield, brownfield or ambiguous
 * @param requirement the requirement text a workflow is started with
 * @param summary     one paragraph explaining what the scenario demonstrates
 * @param fixtureFile path of the readable scenario definition in the repository
 * @param evidence    where a reviewer can see the outcome (tests, endpoints, docs)
 */
@Schema(name = "Scenario", description = "A reproducible assessment scenario")
public record Scenario(String key, String name, ScenarioType type, String requirement, String summary,
		String fixtureFile, List<String> evidence) {

	public Scenario {
		evidence = List.copyOf(evidence);
	}
}
