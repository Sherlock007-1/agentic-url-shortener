package com.agenticsdlc.orchestrator.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.domain.RollbackStatus;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.domain.WorkspaceSnapshot;
import com.agenticsdlc.orchestrator.engine.WorkflowQueryService;
import com.agenticsdlc.orchestrator.engine.WorkflowService;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import com.agenticsdlc.orchestrator.repository.WorkspaceSnapshotRepository;
import com.agenticsdlc.orchestrator.support.AbstractPostgresIntegrationTest;
import com.agenticsdlc.orchestrator.support.TestDatabase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Snapshot/rollback of the run workspace.
 *
 * <p>Uses the temporary workspace root configured for tests, so nothing outside
 * the temp directory is ever touched.
 */
@SpringBootTest
class WorkspaceSnapshotRollbackIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private WorkflowService workflowService;

	@Autowired
	private WorkflowQueryService queryService;

	@Autowired
	private WorkspaceService workspaceService;

	@Autowired
	private WorkspaceSnapshotService snapshotService;

	@Autowired
	private PolicyGuardService policyGuardService;

	@Autowired
	private WorkspaceSnapshotRepository snapshotRepository;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private UUID workflowId;
	private Path workspace;

	@BeforeEach
	void createWorkspace() throws IOException {
		TestDatabase.clean(jdbcTemplate);
		workflowId = workflowService.createWorkflow("Add click analytics for shortened URLs.").getId();
		workspace = workspaceService.workspace(workflowId);
		clean(workspace);
		write(workspace.resolve("src/App.java"), "class App {}");
		write(workspace.resolve("README.md"), "original readme");
	}

	@Test
	void rollingBackRestoresModifiedAndDeletedFilesAndRemovesNewOnes() throws IOException {
		WorkspaceSnapshot snapshot = snapshotService.snapshot(workflowId, null, "before-implementation");
		assertThat(snapshot.getFileCount()).isEqualTo(2);

		write(workspace.resolve("src/App.java"), "class App { void broken() {} }");
		Files.delete(workspace.resolve("README.md"));
		write(workspace.resolve("src/Generated.java"), "class Generated {}");

		snapshotService.rollback(workflowId, snapshot.getId());

		assertThat(read(workspace.resolve("src/App.java"))).isEqualTo("class App {}");
		assertThat(read(workspace.resolve("README.md"))).isEqualTo("original readme");
		assertThat(Files.exists(workspace.resolve("src/Generated.java"))).isFalse();
	}

	@Test
	void snapshotAndRollbackArePersistedAndAudited() {
		WorkspaceSnapshot snapshot = snapshotService.snapshot(workflowId, null, "before-implementation");

		snapshotService.rollback(workflowId, snapshot.getId());

		WorkspaceSnapshot reloaded = snapshotRepository.findById(snapshot.getId()).orElseThrow();
		assertThat(reloaded.getRollbackStatus()).isEqualTo(RollbackStatus.COMPLETED);
		assertThat(reloaded.getRolledBackAt()).isNotNull();
		assertThat(reloaded.getRollbackError()).isNull();
		assertThat(reloaded.getLocation()).contains(workflowId.toString());

		List<AuditEventType> audit = queryService.auditTrail(workflowId).stream()
				.map(event -> event.getEventType())
				.toList();
		assertThat(audit).contains(AuditEventType.SNAPSHOT_CREATED, AuditEventType.ROLLBACK_STARTED,
				AuditEventType.ROLLBACK_COMPLETED);
		assertThat(snapshotService.snapshots(workflowId)).singleElement()
				.satisfies(row -> assertThat(row.getLabel()).isEqualTo("before-implementation"));
	}

	@Test
	void aMutationOutsideTheWorkspaceIsRejectedAuditedAndSafeStopsTheRun() {
		Path outside = workspace.getParent().getParent().resolve("escaped.txt");

		assertThatThrownBy(() -> policyGuardService.requireMutable(workflowId, null, outside))
				.isInstanceOf(PolicyViolationException.class)
				.hasMessageContaining("outside the approved workspace root");

		assertThat(workflowRunRepository.findById(workflowId).orElseThrow().getStatus())
				.isEqualTo(WorkflowStatus.SAFE_STOPPED);
		assertThat(queryService.auditTrail(workflowId)).extracting(event -> event.getEventType())
				.contains(AuditEventType.POLICY_REJECTED, AuditEventType.WORKFLOW_SAFE_STOPPED);
	}

	@Test
	void aSensitiveFileInsideTheWorkspaceIsRejectedToo() {
		assertThatThrownBy(() -> policyGuardService.requireMutable(workflowId, null, workspace.resolve(".env")))
				.isInstanceOf(PolicyViolationException.class)
				.hasMessageContaining("sensitive file");

		assertThat(policyGuardService.evaluate(workflowId, workspace.resolve("src/App.java")).allowed()).isTrue();
	}

	private void write(Path path, String content) throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, content, StandardCharsets.UTF_8);
	}

	private String read(Path path) throws IOException {
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	private void clean(Path directory) throws IOException {
		if (!Files.exists(directory)) {
			return;
		}
		try (var paths = Files.walk(directory)) {
			for (Path path : paths.filter(candidate -> !candidate.equals(directory))
					.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}
}
