package com.agenticsdlc.orchestrator.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The change-control guardrail is unit tested on its own: it is the boundary that
 * later file-mutating agents will run inside.
 *
 * <p>It is an application-level guardrail, not a security sandbox, and the tests
 * assert exactly that scope.
 */
class ChangePolicyGuardTest {

	@TempDir
	Path workspace;

	@Test
	void aPathInsideTheWorkspaceIsAllowed() {
		ChangePolicyGuard guard = new ChangePolicyGuard(workspace);

		assertThat(guard.evaluateMutation(workspace.resolve("src/main/java/App.java")).allowed()).isTrue();
		assertThat(guard.requireMutable(Path.of("src/main/java/App.java")))
				.isEqualTo(workspace.resolve("src/main/java/App.java").toAbsolutePath().normalize());
	}

	@Test
	void aPathOutsideTheWorkspaceIsRejected() {
		ChangePolicyGuard guard = new ChangePolicyGuard(workspace);

		PolicyDecision decision = guard.evaluateMutation(workspace.getParent().resolve("elsewhere/File.java"));

		assertThat(decision.allowed()).isFalse();
		assertThat(decision.reason()).contains("outside the approved workspace root");
	}

	@Test
	void pathTraversalThatEscapesTheWorkspaceIsRejected() {
		ChangePolicyGuard guard = new ChangePolicyGuard(workspace);

		assertThatThrownBy(() -> guard.requireMutable(Path.of("../../etc/passwd")))
				.isInstanceOf(PolicyViolationException.class)
				.hasMessageContaining("outside the approved workspace root");
	}

	@Test
	void sensitiveFilesAreRejectedEvenInsideTheWorkspace() {
		ChangePolicyGuard guard = new ChangePolicyGuard(workspace);

		assertThat(guard.evaluateMutation(workspace.resolve(".env")).reason()).contains("sensitive file");
		assertThat(guard.evaluateMutation(workspace.resolve("config/server.key")).reason())
				.contains("credential/secret material");
		assertThat(guard.evaluateMutation(workspace.resolve("keys/service.pem")).reason())
				.contains("credential/secret material");
		assertThat(guard.evaluateMutation(workspace.resolve(".git/HEAD")).reason())
				.contains("protected path segment");
	}

	@Test
	void theWorkspaceRootItselfMayNotBeDeleted() {
		ChangePolicyGuard guard = new ChangePolicyGuard(workspace);

		PolicyDecision decision = guard.evaluateMutation(workspace);

		assertThat(decision.allowed()).isFalse();
		assertThat(decision.reason()).contains("workspace root itself");
	}

	@Test
	void aMissingTargetIsRejected() {
		assertThat(new ChangePolicyGuard(workspace).evaluateMutation(null).allowed()).isFalse();
	}
}
