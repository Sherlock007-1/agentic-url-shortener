package com.agenticsdlc.orchestrator.scenario;

import com.agenticsdlc.orchestrator.engine.WorkflowNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The three assessment scenarios, so a reviewer can start any of them without
 * retyping a requirement and without guessing what "correct" looks like.
 *
 * <p>Deliberately a static catalog rather than a framework: there is no scenario
 * engine, no DSL and no runtime file loading. The readable definitions live in
 * {@code scenarios/*.json} at the repository root and are referenced from here.
 */
public final class ScenarioCatalog {

	public static final String GREENFIELD_CLICK_ANALYTICS = "greenfield-click-analytics";
	public static final String BROWNFIELD_COLLISION_RETRY = "brownfield-collision-retry";
	public static final String AMBIGUOUS_SECURITY = "ambiguous-security";

	private static final Map<String, Scenario> SCENARIOS = index(List.of(
			new Scenario(GREENFIELD_CLICK_ANALYTICS,
					"Greenfield: click analytics for shortened URLs",
					ScenarioType.GREENFIELD,
					"Add click analytics for shortened URLs.",
					"A capability that did not exist in the baseline: a new Flyway migration, a new entity, a new "
							+ "service and a new read endpoint. The requirement is unambiguous, so the workflow runs "
							+ "the full SDLC graph and only stops at the two human approval gates.",
					"scenarios/greenfield-click-analytics.json",
					List.of("GET /api/urls/{shortCode}/analytics on url-shortener-service (port 8081)",
							"url-shortener-service test: ClickAnalyticsIntegrationTest",
							"docs/SCENARIOS.md")),
			new Scenario(BROWNFIELD_COLLISION_RETRY,
					"Brownfield: collision-safe short-code generation",
					ScenarioType.BROWNFIELD,
					"Make short-code generation collision-safe by retrying generation up to three times before failing.",
					"A change to behaviour that already existed: the baseline created a short URL in one attempt and "
							+ "let the unique constraint fail the request. The increment bounds generation to three "
							+ "attempts, retries only a duplicate short code, and keeps the database constraint as the "
							+ "final safety boundary.",
					"scenarios/brownfield-collision-retry.json",
					List.of("url-shortener-service tests: ShortUrlServiceCollisionRetryTest, "
							+ "ShortCodeCollisionRetryIntegrationTest, ShortCodeCollisionsTest",
							"docs/SCENARIOS.md")),
			new Scenario(AMBIGUOUS_SECURITY,
					"Ambiguous: make shortened URLs more secure",
					ScenarioType.AMBIGUOUS,
					"Make shortened URLs more secure.",
					"A requirement that names a goal instead of a change. The workflow parks in "
							+ "AWAITING_CLARIFICATION with a persisted question instead of guessing; answering it with "
							+ "replan=true creates graph version 2 while version 1 stays queryable. Under the clarified "
							+ "requirement the agents record that the behaviour already exists and that no duplicate "
							+ "implementation is required.",
					"scenarios/ambiguous-security.json",
					List.of("GET /api/workflows/{id}/clarifications",
							"POST /api/workflows/{id}/clarifications/{clarificationId}/answer (replan=true)",
							"GET /api/workflows/{id}/graph/versions",
							"GET /api/workflows/{id}/decisions and /audit",
							"orchestrator-service test: AmbiguousRequirementScenarioIntegrationTest",
							"docs/SCENARIOS.md"))));

	private ScenarioCatalog() {
	}

	public static List<Scenario> all() {
		return List.copyOf(SCENARIOS.values());
	}

	public static Optional<Scenario> find(String key) {
		return Optional.ofNullable(SCENARIOS.get(key));
	}

	/** @throws WorkflowNotFoundException when the key is unknown (mapped to HTTP 404) */
	public static Scenario require(String key) {
		return find(key).orElseThrow(() -> new WorkflowNotFoundException("Scenario '" + key + "' not found. Known keys: "
				+ String.join(", ", SCENARIOS.keySet())));
	}

	private static Map<String, Scenario> index(List<Scenario> scenarios) {
		// LinkedHashMap, not Map.copyOf: the catalog is listed in declaration order.
		Map<String, Scenario> byKey = new LinkedHashMap<>();
		for (Scenario scenario : scenarios) {
			byKey.put(scenario.key(), scenario);
		}
		return java.util.Collections.unmodifiableMap(byKey);
	}
}
