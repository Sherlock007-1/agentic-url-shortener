package com.agenticsdlc.orchestrator.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.agenticsdlc.orchestrator.agent.RetryableAgentException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

/**
 * Retry classification decides whether a failure may be repeated at all, so it is
 * unit tested separately from the engine.
 */
class FailureClassifierTest {

	private final FailureClassifier classifier = new FailureClassifier();

	@Test
	void anExplicitRetryableFailureIsRetryable() {
		assertThat(classifier.isRetryable(new RetryableAgentException("transient upstream error"))).isTrue();
	}

	@Test
	void aRetryableCauseAnywhereInTheChainIsRetryable() {
		Exception wrapped = new IllegalStateException("wrapper",
				new RetryableAgentException("transient", new TimeoutException("slow")));

		assertThat(classifier.isRetryable(wrapped)).isTrue();
	}

	@Test
	void anAgentTimeoutIsRetryable() {
		assertThat(classifier.isRetryable(new AgentTimeoutException("implementation", Duration.ofSeconds(1)))).isTrue();
	}

	@Test
	void anOrdinaryDefectIsNotRetryable() {
		assertThat(classifier.isRetryable(new IllegalStateException("agent exploded"))).isFalse();
		assertThat(classifier.isRetryable(new NullPointerException())).isFalse();
	}

	@Test
	void aFailureWithoutMessageIsDescribedByItsType() {
		assertThat(classifier.describe(new NullPointerException())).isEqualTo("NullPointerException");
		assertThat(classifier.describe(new IllegalStateException("boom"))).isEqualTo("boom");
	}
}
