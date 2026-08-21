package com.agenticsdlc.orchestrator.governance;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Change-control guardrail for file mutations performed on behalf of agents.
 *
 * <p>Demonstrates that agents do not get unrestricted autonomy over the machine.
 * Three rules, all evaluated on normalised absolute paths:
 *
 * <ol>
 * <li>a mutation must stay inside the approved workspace root,</li>
 * <li>path traversal ({@code ..}) that escapes the workspace is rejected,</li>
 * <li>sensitive files (credentials, secrets, keystores, VCS metadata) are never
 * writable, even inside the workspace.</li>
 * </ol>
 *
 * <p>This is an application-level guardrail, <strong>not</strong> a security
 * sandbox: it does not stop native code, symlink races or a process that bypasses
 * it. It exists to make the boundary of autonomous change explicit and testable.
 */
public class ChangePolicyGuard {

	/** File names that are never mutable, regardless of location. */
	private static final List<String> BLOCKED_NAMES = List.of(".env", ".env.local", "id_rsa", "id_ed25519",
			"credentials", "credentials.json", "secrets.yml", "secrets.yaml", "application-secrets.yml");

	/** Suffixes that indicate credential/secret material. */
	private static final List<String> BLOCKED_SUFFIXES = List.of(".pem", ".key", ".p12", ".jks", ".keystore", ".pfx");

	/** Path segments that must never be touched. */
	private static final List<String> BLOCKED_SEGMENTS = List.of(".git", ".ssh", ".aws", ".gnupg");

	private final Path workspaceRoot;

	public ChangePolicyGuard(Path workspaceRoot) {
		this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
	}

	public Path workspaceRoot() {
		return workspaceRoot;
	}

	/** Evaluates a mutation request without throwing. */
	public PolicyDecision evaluateMutation(Path target) {
		if (target == null) {
			return PolicyDecision.reject("No target path supplied");
		}
		Path candidate = target.isAbsolute() ? target.normalize()
				: workspaceRoot.resolve(target).toAbsolutePath().normalize();

		if (!candidate.startsWith(workspaceRoot)) {
			return PolicyDecision.reject(
					"Mutation of '" + candidate + "' is outside the approved workspace root '" + workspaceRoot + "'");
		}
		if (candidate.equals(workspaceRoot)) {
			return PolicyDecision.reject("The workspace root itself may not be mutated or deleted");
		}
		for (Path segment : workspaceRoot.relativize(candidate)) {
			String name = segment.toString().toLowerCase(Locale.ROOT);
			if (BLOCKED_SEGMENTS.contains(name)) {
				return PolicyDecision.reject("Mutation of protected path segment '" + name + "' is not permitted");
			}
			if (BLOCKED_NAMES.contains(name)) {
				return PolicyDecision.reject("Mutation of sensitive file '" + name + "' is not permitted");
			}
			if (BLOCKED_SUFFIXES.stream().anyMatch(name::endsWith)) {
				return PolicyDecision.reject(
						"Mutation of credential/secret material '" + name + "' is not permitted");
			}
		}
		return PolicyDecision.allow();
	}

	/**
	 * Resolves a path for mutation, enforcing the policy.
	 *
	 * @throws PolicyViolationException when the mutation is not permitted
	 */
	public Path requireMutable(Path target) {
		evaluateMutation(target).throwIfRejected();
		return target.isAbsolute() ? target.normalize()
				: workspaceRoot.resolve(target).toAbsolutePath().normalize();
	}
}
