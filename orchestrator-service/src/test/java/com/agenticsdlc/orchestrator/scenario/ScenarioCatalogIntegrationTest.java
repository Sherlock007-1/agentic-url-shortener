package com.agenticsdlc.orchestrator.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agenticsdlc.orchestrator.engine.WorkflowNotFoundException;
import com.agenticsdlc.orchestrator.governance.AmbiguityDetector;
import com.agenticsdlc.orchestrator.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The scenario catalog is the reproducibility contract: the requirement a reviewer
 * demos must be exactly the one the tests and fixtures describe.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ScenarioCatalogIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private final AmbiguityDetector ambiguityDetector = new AmbiguityDetector();

	@Test
	void theCatalogHoldsExactlyTheThreeAssessmentScenarios() {
		assertThat(ScenarioCatalog.all()).extracting(Scenario::key)
				.containsExactly(ScenarioCatalog.GREENFIELD_CLICK_ANALYTICS, ScenarioCatalog.BROWNFIELD_COLLISION_RETRY,
						ScenarioCatalog.AMBIGUOUS_SECURITY);
		assertThat(ScenarioCatalog.all()).extracting(Scenario::type)
				.containsExactly(ScenarioType.GREENFIELD, ScenarioType.BROWNFIELD, ScenarioType.AMBIGUOUS);
		assertThat(ScenarioCatalog.all()).allSatisfy(scenario -> {
			assertThat(scenario.requirement()).isNotBlank();
			assertThat(scenario.summary()).isNotBlank();
			assertThat(scenario.fixtureFile()).isEqualTo("scenarios/" + scenario.key() + ".json");
			assertThat(scenario.evidence()).isNotEmpty();
		});
	}

	@Test
	void onlyTheAmbiguousScenarioTriggersTheClarificationGate() {
		assertThat(ScenarioCatalog.all())
				.filteredOn(scenario -> !ambiguityDetector.isClear(scenario.requirement()))
				.extracting(Scenario::key)
				.containsExactly(ScenarioCatalog.AMBIGUOUS_SECURITY);
	}

	@Test
	void anUnknownScenarioKeyIsANotFound() throws Exception {
		assertThatThrownBy(() -> ScenarioCatalog.require("does-not-exist"))
				.isInstanceOf(WorkflowNotFoundException.class);

		mockMvc.perform(get("/api/scenarios/{key}", "does-not-exist"))
				.andExpect(status().isNotFound());
	}

	@Test
	void theCatalogIsExposedOverTheApi() throws Exception {
		mockMvc.perform(get("/api/scenarios"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[2].key").value(ScenarioCatalog.AMBIGUOUS_SECURITY))
				.andExpect(jsonPath("$[2].type").value("AMBIGUOUS"));

		mockMvc.perform(get("/api/scenarios/{key}", ScenarioCatalog.BROWNFIELD_COLLISION_RETRY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("BROWNFIELD"))
				.andExpect(jsonPath("$.fixtureFile").value("scenarios/brownfield-collision-retry.json"));
	}

	@Test
	void theScenarioApiIsPublishedInOpenApi() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/scenarios']").exists())
				.andExpect(jsonPath("$.paths['/api/scenarios/{key}/start']").exists());
	}
}
