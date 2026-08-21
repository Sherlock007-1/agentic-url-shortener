package com.agenticsdlc.orchestrator.governance;

import com.agenticsdlc.orchestrator.agent.RetryableAgentException;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/**
 * Decides whether an agent failure deserves another bounded attempt.
 *
 * <p>Retryable: an explicit {@link RetryableAgentException} anywhere in the cause
 * chain, or an agent attempt that hit its execution timeout. Everything else is
 * permanent - a deterministic defect must fail fast instead of looping.
 */
@Component
public class FailureClassifier {

	public boolean isRetryable(Throwable failure) {
		for (Throwable current = failure; current != null; current = current.getCause()) {
			if (current instanceof RetryableAgentException || current instanceof TimeoutException
					|| current instanceof AgentTimeoutException) {
				return true;
			}
			if (current.getCause() == current) {
				break;
			}
		}
		return false;
	}

	public String describe(Throwable failure) {
		String message = failure.getMessage();
		return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
	}
}
