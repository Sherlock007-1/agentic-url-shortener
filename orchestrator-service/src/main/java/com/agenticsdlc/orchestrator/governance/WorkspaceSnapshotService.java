package com.agenticsdlc.orchestrator.governance;

import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.domain.WorkspaceSnapshot;
import com.agenticsdlc.orchestrator.engine.AuditService;
import com.agenticsdlc.orchestrator.engine.WorkflowNotFoundException;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import com.agenticsdlc.orchestrator.repository.WorkspaceSnapshotRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Smallest practical rollback mechanism: copy the workspace directory before a
 * mutating stage, copy it back when the stage has to be undone.
 *
 * <p>Deliberately <em>not</em> implemented: Git-based rollback, content-addressed
 * artefact stores, distributed storage or automatic snapshots of the repository on
 * every task. A snapshot is only taken when a stage explicitly requests one.
 *
 * <p>Every restored path goes through {@link PolicyGuardService}, so a rollback can
 * never write outside the approved workspace either.
 */
@Service
public class WorkspaceSnapshotService {

	private final WorkspaceService workspaceService;
	private final WorkspaceSnapshotRepository snapshotRepository;
	private final WorkflowRunRepository workflowRunRepository;
	private final PolicyGuardService policyGuardService;
	private final AuditService auditService;
	private final Clock clock;

	public WorkspaceSnapshotService(WorkspaceService workspaceService, WorkspaceSnapshotRepository snapshotRepository,
			WorkflowRunRepository workflowRunRepository, PolicyGuardService policyGuardService,
			AuditService auditService, Clock clock) {
		this.workspaceService = workspaceService;
		this.snapshotRepository = snapshotRepository;
		this.workflowRunRepository = workflowRunRepository;
		this.policyGuardService = policyGuardService;
		this.auditService = auditService;
		this.clock = clock;
	}

	/** Copies the current workspace into a new snapshot directory. */
	@Transactional
	public WorkspaceSnapshot snapshot(UUID workflowRunId, UUID taskId, String label) {
		requireRun(workflowRunId);
		Path workspace = workspaceService.workspace(workflowRunId);
		UUID snapshotId = UUID.randomUUID();
		Path target = workspaceService.snapshotsDirectory(workflowRunId).resolve(snapshotId.toString());

		int fileCount;
		try {
			Files.createDirectories(target);
			fileCount = copyTree(workspace, target);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Unable to snapshot workspace of workflow " + workflowRunId, ex);
		}

		WorkspaceSnapshot snapshot = snapshotRepository.save(new WorkspaceSnapshot(workflowRunId, taskId, label,
				target.toString(), fileCount, clock.instant()));
		auditService.record(workflowRunId, taskId, AuditEventType.SNAPSHOT_CREATED,
				"Workspace snapshot '" + label + "' created (" + fileCount + " files)", target.toString());
		return snapshot;
	}

	@Transactional(readOnly = true)
	public List<WorkspaceSnapshot> snapshots(UUID workflowRunId) {
		requireRun(workflowRunId);
		return snapshotRepository.findByWorkflowRunIdOrderByCreatedAtAsc(workflowRunId);
	}

	/**
	 * Restores the workspace from a snapshot: the workspace is emptied and the
	 * snapshot content copied back, so files created after the snapshot disappear
	 * and modified/deleted files are restored byte for byte.
	 */
	@Transactional
	public WorkspaceSnapshot rollback(UUID workflowRunId, UUID snapshotId) {
		WorkflowRun run = requireRun(workflowRunId);
		WorkspaceSnapshot snapshot = snapshotRepository.findById(snapshotId)
				.orElseThrow(() -> new WorkflowNotFoundException("Snapshot " + snapshotId + " not found"));
		if (!snapshot.getWorkflowRunId().equals(workflowRunId)) {
			throw new WorkflowNotFoundException(
					"Snapshot " + snapshotId + " does not belong to workflow " + workflowRunId);
		}

		WorkflowStatus previousStatus = run.getStatus();
		snapshot.rollbackStarted();
		snapshotRepository.save(snapshot);
		if (!previousStatus.isTerminal()) {
			run.markRollingBack();
			workflowRunRepository.save(run);
		}
		auditService.record(workflowRunId, snapshot.getTaskId(), AuditEventType.ROLLBACK_STARTED,
				"Rollback started from snapshot '" + snapshot.getLabel() + "'", snapshot.getLocation());

		Path workspace = workspaceService.workspace(workflowRunId);
		Path source = Path.of(snapshot.getLocation());
		Instant now = clock.instant();
		try {
			clearDirectory(workflowRunId, snapshot.getTaskId(), workspace);
			int restored = copyTree(source, workspace);
			snapshot.rollbackCompleted(now);
			snapshotRepository.save(snapshot);
			restorePreviousStatus(run, previousStatus, now);
			auditService.record(workflowRunId, snapshot.getTaskId(), AuditEventType.ROLLBACK_COMPLETED,
					"Rollback completed, " + restored + " files restored", snapshot.getLocation());
			return snapshot;
		}
		catch (IOException | RuntimeException ex) {
			String error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
			snapshot.rollbackFailed(error, now);
			snapshotRepository.save(snapshot);
			auditService.record(workflowRunId, snapshot.getTaskId(), AuditEventType.ROLLBACK_FAILED,
					"Rollback failed", error);
			throw new RollbackFailedException(
					"Rollback of workflow " + workflowRunId + " from snapshot " + snapshotId + " failed: " + error, ex);
		}
	}

	private void restorePreviousStatus(WorkflowRun run, WorkflowStatus previousStatus, Instant now) {
		if (previousStatus.isTerminal()) {
			return;
		}
		if (previousStatus == WorkflowStatus.RUNNING) {
			run.markRunning(now);
		}
		else {
			// Parked runs stay parked: a rollback does not silently grant autonomy.
			run.markReady(run.getCurrentGraphVersion());
		}
		workflowRunRepository.save(run);
	}

	private int copyTree(Path source, Path target) throws IOException {
		if (!Files.exists(source)) {
			return 0;
		}
		int[] files = { 0 };
		Files.walkFileTree(source, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Files.createDirectories(target.resolve(source.relativize(dir).toString()));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.copy(file, target.resolve(source.relativize(file).toString()),
						StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
				files[0]++;
				return FileVisitResult.CONTINUE;
			}
		});
		return files[0];
	}

	/** Deletes workspace content; every deletion is policy-checked. */
	private void clearDirectory(UUID workflowRunId, UUID taskId, Path directory) throws IOException {
		if (!Files.exists(directory)) {
			return;
		}
		try (Stream<Path> entries = Files.walk(directory)) {
			List<Path> ordered = entries.filter(path -> !path.equals(directory))
					.sorted((left, right) -> right.getNameCount() - left.getNameCount())
					.toList();
			for (Path path : ordered) {
				policyGuardService.requireMutable(workflowRunId, taskId, path);
				Files.deleteIfExists(path);
			}
		}
	}

	private WorkflowRun requireRun(UUID workflowRunId) {
		return workflowRunRepository.findById(workflowRunId)
				.orElseThrow(() -> new WorkflowNotFoundException("Workflow " + workflowRunId + " not found"));
	}
}
