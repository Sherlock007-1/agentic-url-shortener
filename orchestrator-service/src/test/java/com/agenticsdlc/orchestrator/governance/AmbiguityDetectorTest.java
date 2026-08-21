package com.agenticsdlc.orchestrator.governance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Ambiguity detection must be narrow.
 *
 * <p>The negative cases matter most: if every requirement looked ambiguous, the
 * clarification gate would become a permanent stop and the scenario would prove
 * nothing.
 */
class AmbiguityDetectorTest {

	private final AmbiguityDetector detector = new AmbiguityDetector();

	@Test
	void theScenarioRequirementIsRecognisedAsAmbiguous() {
		assertThat(detector.detect("Make shortened URLs more secure.")).hasValueSatisfying(ambiguity -> {
			assertThat(ambiguity.key()).isEqualTo("ambiguous-security");
			assertThat(ambiguity.question()).contains("Which security improvement is intended");
			assertThat(ambiguity.rationale()).contains("without naming a threat");
		});
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"Make shortened URLs more secure.",
			"make the short urls MORE SECURE",
			"Improve security of the redirect endpoint.",
			"We need better security for shortened links.",
			"Make the API secure."
	})
	void knownVagueSecurityPhrasingsAreAmbiguous(String requirement) {
		assertThat(detector.detect(requirement)).isPresent();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"Add click analytics for shortened URLs.",
			"Make short-code generation collision-safe by retrying generation up to three times before failing.",
			"Reject unsafe URL schemes and require HTTP or HTTPS URLs with a valid host.",
			"Require an API key on the management endpoints.",
			"Rate limit redirects to 100 requests per minute per client.",
			"Add click analytics with a 90 day retention window."
	})
	void actionableRequirementsAreNotTreatedAsAmbiguous(String requirement) {
		assertThat(detector.detect(requirement)).isEmpty();
		assertThat(detector.isClear(requirement)).isTrue();
	}

	@Test
	void theExampleClarificationAnswerIsItselfActionable() {
		// Otherwise replanning would immediately park the new graph version again.
		assertThat(detector.isClear("Reject unsafe URL schemes and require HTTP or HTTPS URLs with a valid host."))
				.isTrue();
	}

	@Test
	void blankAndNullRequirementsAreNotAmbiguityMatches() {
		assertThat(detector.detect(null)).isEmpty();
		assertThat(detector.detect("   ")).isEmpty();
	}
}
