package com.agenticsdlc.orchestrator.metrics;

import com.agenticsdlc.orchestrator.domain.ApprovalStatus;
import com.agenticsdlc.orchestrator.domain.AttemptOutcome;
import com.agenticsdlc.orchestrator.domain.AuditEvent;
import com.agenticsdlc.orchestrator.domain.AuditEventType;
import com.agenticsdlc.orchestrator.domain.RollbackStatus;
import com.agenticsdlc.orchestrator.domain.TaskAttempt;
import com.agenticsdlc.orchestrator.domain.WorkflowRun;
import com.agenticsdlc.orchestrator.domain.WorkflowStatus;
import com.agenticsdlc.orchestrator.repository.ApprovalRepository;
import com.agenticsdlc.orchestrator.repository.AuditEventRepository;
import com.agenticsdlc.orchestrator.repository.TaskAttemptRepository;
import com.agenticsdlc.orchestrator.repository.WorkflowRunRepository;
import com.agenticsdlc.orchestrator.repository.WorkspaceSnapshotRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes orchestration metrics from persisted records.
 *
 * <p>Formulas (all populations are the persisted rows, no sampling, no smoothing):
 *
 * <ul>
 * <li><b>success rate</b> = COMPLETED / (COMPLETED + FAILED + SAFE_STOPPED); null
 * when no run is terminal yet.</li>
 * <li><b>agent retries</b> = number of {@code task_attempts} rows with
 * {@code attempt_no &gt; 1}. The engine's optimistic-locking retries are a
 * concurrency mechanism and are intentionally excluded.</li>
 * <li><b>rollbacks</b> = snapshots with {@code rollback_status = COMPLETED}.</li>
 * <li><b>MTTR</b> = mean over tasks that first failed and later succeeded of
 * (completion of the first successful attempt - completion of the first failed
 * attempt); null when no such task exists.</li>
 * <li><b>latency</b> = mean/max of (completedAt - startedAt) over COMPLETED runs;
 * null when no run completed.</li>
 * </ul>
 *
 * <p>No metric is ever fabricated: without samples the value is {@code null} and
 * counters are {@code 0}.
 */
@Service
public class MetricsService {

	private final WorkflowRunRepository workflowRunRepository;
	private final TaskAttemptRepository attemptRepository;
	private final WorkspaceSnapshotRepository snapshotRepository;
	private final ApprovalRepository approvalRepository;
	private final AuditEventRepository auditEventRepository;

	public MetricsService(WorkflowRunRepository workflowRunRepository, TaskAttemptRepository attemptRepository,
			WorkspaceSnapshotRepository snapshotRepository, ApprovalRepository approvalRepository,
			AuditEventRepository auditEventRepository) {
		this.workflowRunRepository = workflowRunRepository;
		this.attemptRepository = attemptRepository;
		this.snapshotRepository = snapshotRepository;
		this.approvalRepository = approvalRepository;
		this.auditEventRepository = auditEventRepository;
	}

	@Transactional(readOnly = true)
	public MetricsResponse metrics() {
		List<WorkflowRun> runs = workflowRunRepository.findAll();
		long total = runs.size();
		long completed = runs.stream().filter(run -> run.getStatus() == WorkflowStatus.COMPLETED).count();
		long failed = runs.stream().filter(run -> run.getStatus() == WorkflowStatus.FAILED).count();
		long safeStopped = runs.stream().filter(run -> run.getStatus() == WorkflowStatus.SAFE_STOPPED).count();
		long inFlight = runs.stream().filter(run -> !run.getStatus().isTerminal()).count();
		long terminal = completed + failed + safeStopped;
		Double successRate = terminal == 0 ? null : (double) completed / terminal;

		List<TaskAttempt> attempts = attemptRepository.findAll();
		long retries = attempts.stream().filter(attempt -> attempt.getAttemptNo() > 1).count();
		Double retriesPerWorkflow = total == 0 ? null : (double) retries / total;

		long rollbacks = snapshotRepository.countByRollbackStatus(RollbackStatus.COMPLETED);
		long snapshots = snapshotRepository.count();
		Double rollbacksPerWorkflow = total == 0 ? null : (double) rollbacks / total;

		Recovery recovery = meanTimeToRecovery(attempts);
		Latency latency = workflowLatency(runs);
		long fallbacks = auditEventRepository.findAll().stream()
				.map(AuditEvent::getEventType)
				.filter(type -> type == AuditEventType.FALLBACK_INVOKED)
				.count();

		return new MetricsResponse(total, completed, failed, safeStopped, inFlight, successRate, retries,
				retriesPerWorkflow, attempts.size(), fallbacks, rollbacks, rollbacksPerWorkflow, snapshots,
				recovery.meanSeconds(), recovery.samples(), latency.meanSeconds(), latency.maxSeconds(),
				approvalRepository.countByStatus(ApprovalStatus.PENDING));
	}

	/** Mean time between the first failed attempt and the first later success. */
	private Recovery meanTimeToRecovery(List<TaskAttempt> attempts) {
		Map<UUID, List<TaskAttempt>> byTask = new LinkedHashMap<>();
		for (TaskAttempt attempt : attempts) {
			byTask.computeIfAbsent(attempt.getTaskId(), key -> new ArrayList<>()).add(attempt);
		}
		List<Double> samples = new ArrayList<>();
		for (List<TaskAttempt> taskAttempts : byTask.values()) {
			taskAttempts.sort((left, right) -> Integer.compare(left.getAttemptNo(), right.getAttemptNo()));
			Instant firstFailure = null;
			for (TaskAttempt attempt : taskAttempts) {
				if (attempt.getOutcome() == AttemptOutcome.FAILED && firstFailure == null) {
					firstFailure = attempt.getCompletedAt();
				}
				else if (attempt.getOutcome() == AttemptOutcome.SUCCEEDED && firstFailure != null
						&& attempt.getCompletedAt() != null) {
					samples.add(seconds(Duration.between(firstFailure, attempt.getCompletedAt())));
					break;
				}
			}
		}
		OptionalDouble mean = samples.stream().mapToDouble(Double::doubleValue).average();
		return new Recovery(mean.isPresent() ? mean.getAsDouble() : null, samples.size());
	}

	private Latency workflowLatency(List<WorkflowRun> runs) {
		List<Double> samples = runs.stream()
				.filter(run -> run.getStatus() == WorkflowStatus.COMPLETED)
				.filter(run -> run.getStartedAt() != null && run.getCompletedAt() != null)
				.map(run -> seconds(Duration.between(run.getStartedAt(), run.getCompletedAt())))
				.toList();
		if (samples.isEmpty()) {
			return new Latency(null, null);
		}
		double mean = samples.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
		double max = samples.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
		return new Latency(mean, max);
	}

	private double seconds(Duration duration) {
		return duration.toNanos() / 1_000_000_000.0d;
	}

	private record Recovery(Double meanSeconds, long samples) {
	}

	private record Latency(Double meanSeconds, Double maxSeconds) {
	}
}
