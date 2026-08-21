package com.agenticsdlc.orchestrator.governance;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Recognises the small, explicit set of requirement phrasings this prototype knows
 * to be ambiguous.
 *
 * <p>This is deliberately <em>not</em> a general "is this requirement clear?"
 * classifier. Treating every requirement as potentially ambiguous would turn the
 * clarification gate into a permanent stop and would be dishonest about what a
 * deterministic agent can actually infer. Instead there is a short list of known
 * ambiguity patterns; anything that does not match one of them proceeds normally.
 *
 * <p>The clarification <em>mechanism</em> lives in {@link ClarificationService} and
 * is generic. Only the triggering is scenario-specific, and it is isolated here so
 * a reviewer can see exactly what causes a workflow to stop and ask.
 */
@Component
public class AmbiguityDetector {

	/**
	 * A recognised ambiguity.
	 *
	 * @param key       stable identifier, also used as the scenario key
	 * @param question  the question a human has to answer
	 * @param rationale why the requirement cannot be actioned as written
	 */
	public record Ambiguity(String key, String question, String rationale) {
	}

	private record Rule(Pattern pattern, Ambiguity ambiguity) {
	}

	/**
	 * "Make shortened URLs more secure." names a goal, not a change: it does not say
	 * which threat is in scope, so any autonomous choice would be a guess.
	 */
	static final Ambiguity VAGUE_SECURITY = new Ambiguity("ambiguous-security",
			"\"Secure\" can mean several different changes here. Which security improvement is intended: "
					+ "(a) rejecting unsafe URL schemes / requiring valid http(s) destinations, "
					+ "(b) authenticating the management API, "
					+ "(c) rate limiting redirects, or "
					+ "(d) making short codes unguessable? Please state the intended behaviour.",
			"The requirement states a security goal without naming a threat, an asset or an acceptance "
					+ "criterion. Input validation, authentication, rate limiting and code entropy are different "
					+ "changes with different risks, so continuing autonomously would mean guessing which one the "
					+ "stakeholder meant.");

	private static final List<Rule> RULES = List.of(
			new Rule(Pattern.compile(
					"\\b(more|better|improved?|enhanced?|increase[d]?|stronger|tighter)\\s+secur(e|ity)\\b"
							+ "|\\bmake\\s+[^.]{0,60}?\\bsecure\\b"
							+ "|\\bsecure\\s+it\\b"),
					VAGUE_SECURITY));

	/**
	 * @param requirement raw requirement text
	 * @return the recognised ambiguity, or empty when the requirement may proceed
	 */
	public Optional<Ambiguity> detect(String requirement) {
		if (requirement == null || requirement.isBlank()) {
			return Optional.empty();
		}
		String normalised = requirement.toLowerCase(Locale.ROOT);
		return RULES.stream()
				.filter(rule -> rule.pattern().matcher(normalised).find())
				.map(Rule::ambiguity)
				.findFirst();
	}

	/** True when the text no longer matches any known ambiguity pattern. */
	public boolean isClear(String requirement) {
		return detect(requirement).isEmpty();
	}
}
