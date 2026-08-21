package com.agenticsdlc.orchestrator.governance;

import com.agenticsdlc.orchestrator.config.GovernanceProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Filesystem layout of a run:
 *
 * <pre>
 * {workspace-root}/{workflowId}/workspace/    &lt;- the only mutable area
 * {workspace-root}/{workflowId}/snapshots/{snapshotId}/
 * </pre>
 *
 * <p>Nothing is copied automatically: a workspace is created empty and snapshots
 * are only taken when a stage explicitly asks for one.
 */
@Component
public class WorkspaceService {

	private final Path root;

	public WorkspaceService(GovernanceProperties governance) {
		this.root = Path.of(governance.workspaceRoot()).toAbsolutePath().normalize();
	}

	public Path root() {
		return root;
	}

	public Path runDirectory(UUID workflowRunId) {
		return root.resolve(workflowRunId.toString());
	}

	/** The mutable workspace of a run, created on first use. */
	public Path workspace(UUID workflowRunId) {
		return createDirectories(runDirectory(workflowRunId).resolve("workspace"));
	}

	public Path snapshotsDirectory(UUID workflowRunId) {
		return createDirectories(runDirectory(workflowRunId).resolve("snapshots"));
	}

	/** The change-policy guard rooted at the workspace of this run. */
	public ChangePolicyGuard guardFor(UUID workflowRunId) {
		return new ChangePolicyGuard(workspace(workflowRunId));
	}

	private Path createDirectories(Path path) {
		try {
			return Files.createDirectories(path);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Unable to create directory " + path, ex);
		}
	}
}
