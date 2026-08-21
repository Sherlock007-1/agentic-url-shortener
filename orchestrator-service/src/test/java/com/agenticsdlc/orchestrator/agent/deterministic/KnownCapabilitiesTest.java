package com.agenticsdlc.orchestrator.agent.deterministic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The "this already exists" knowledge must be as narrow as the ambiguity rules:
 * claiming a capability exists when it does not would be worse than implementing it.
 */
class KnownCapabilitiesTest {

	@Test
	void theClarifiedSecurityRequirementMatchesTheExistingValidation() {
		assertThat(KnownCapabilities.match("Reject unsafe URL schemes and require HTTP or HTTPS URLs with a valid host."))
				.hasValueSatisfying(capability -> {
					assertThat(capability.key()).isEqualTo("url-scheme-validation");
					assertThat(capability.evidence()).contains("UrlValidator");
				});
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"Add click analytics for shortened URLs.",
			"Make short-code generation collision-safe by retrying generation up to three times before failing.",
			"Require an API key on the management endpoints.",
			"Rate limit redirects to 100 requests per minute per client."
	})
	void unrelatedRequirementsMatchNoExistingCapability(String requirement) {
		assertThat(KnownCapabilities.match(requirement)).isEmpty();
	}

	@Test
	void blankInputMatchesNothing() {
		assertThat(KnownCapabilities.match(null)).isEmpty();
		assertThat(KnownCapabilities.match("  ")).isEmpty();
	}

	@Test
	void bothDecisionsExplainThemselvesForTheAuditTrail() {
		var capability = KnownCapabilities.URL_SCHEME_VALIDATION;

		assertThat(KnownCapabilities.alreadyImplementedDecision(capability).rationale())
				.contains("Evidence:")
				.contains("duplicate behaviour");
		assertThat(KnownCapabilities.noChangeRequiredDecision(capability).rationale())
				.contains("already satisfied")
				.contains("recorded outcome");
	}
}
