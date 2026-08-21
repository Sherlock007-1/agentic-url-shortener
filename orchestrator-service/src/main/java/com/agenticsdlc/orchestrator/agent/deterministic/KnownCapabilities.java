package com.agenticsdlc.orchestrator.agent.deterministic;

import com.agenticsdlc.orchestrator.agent.AgentDecision;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A tiny registry of capabilities the target codebase is already known to have.
 *
 * <p>It exists so the deterministic agents can produce the engineering outcome that
 * matters after a clarification: <em>the clarified behaviour already exists, so no
 * duplicate implementation is required</em>. Discovering that during codebase
 * analysis and recording it as a decision is more valuable than writing the feature
 * twice.
 *
 * <p>This is knowledge configured by a human, not knowledge derived by the agents.
 * It is intentionally tiny and hard-coded: a real system would inspect the
 * repository, and that is explicitly out of scope for this prototype.
 */
public final class KnownCapabilities {

	/**
	 * An existing capability of the URL shortener.
	 *
	 * @param key       stable identifier
	 * @param summary   what the capability does
	 * @param evidence  where it is implemented and tested
	 */
	public record Capability(String key, String summary, String evidence) {
	}

	private record Entry(Pattern pattern, Capability capability) {
	}

	static final Capability URL_SCHEME_VALIDATION = new Capability("url-scheme-validation",
			"Destination URLs are already validated: only absolute http/https URIs with a host and at most "
					+ "2048 characters are accepted, and schemes such as javascript:, file: and data: are rejected.",
			"url-shortener-service: UrlValidator, ShortUrlService.create, ShortUrlExceptionHandler (HTTP 400); "
					+ "tests: UrlValidatorTest, ShortUrlServiceTest.createRejectsInvalidDestinationUrl, "
					+ "ShortUrlApiIntegrationTest.rejectsInvalidDestinationUrl.");

	private static final List<Entry> ENTRIES = List.of(
			new Entry(Pattern.compile(
					"(unsafe|reject).{0,40}scheme|\\bhttps?\\b.{0,40}\\b(valid\\s+host|host)\\b"
							+ "|\\bvalid(ate|ation)\\b.{0,40}\\burl\\b"),
					URL_SCHEME_VALIDATION));

	private KnownCapabilities() {
	}

	/** The existing capability a requirement asks for, if the prototype knows one. */
	public static Optional<Capability> match(String requirement) {
		if (requirement == null || requirement.isBlank()) {
			return Optional.empty();
		}
		String normalised = requirement.toLowerCase(Locale.ROOT);
		return ENTRIES.stream()
				.filter(entry -> entry.pattern().matcher(normalised).find())
				.map(Entry::capability)
				.findFirst();
	}

	/** Decision recorded by codebase analysis when the capability is already present. */
	public static AgentDecision alreadyImplementedDecision(Capability capability) {
		return new AgentDecision("CODEBASE_ANALYSIS",
				"Requested capability already exists: " + capability.key(),
				capability.summary() + " Evidence: " + capability.evidence()
						+ " Re-implementing it would duplicate behaviour and add regression risk without adding value.");
	}

	/** Decision recorded by the implementation stage when no code change is required. */
	public static AgentDecision noChangeRequiredDecision(Capability capability) {
		return new AgentDecision("IMPLEMENTATION",
				"No code change required for " + capability.key(),
				"Codebase analysis established that the clarified requirement is already satisfied ("
						+ capability.evidence() + "). The implementation stage therefore proposes no change and the "
						+ "workflow proceeds to verification and documentation. Deciding not to write code is a "
						+ "recorded outcome, not a skipped step.");
	}
}
