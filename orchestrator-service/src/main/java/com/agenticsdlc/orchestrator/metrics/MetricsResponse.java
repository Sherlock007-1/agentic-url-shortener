package com.agenticsdlc.orchestrator.metrics;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Orchestration metrics, all derived from persisted workflow/task/attempt/audit
 * rows. Nothing is estimated: a metric without samples is {@code null}.
 *
 * @param workflowsTotal          all persisted runs
 * @param workflowsCompleted      runs in COMPLETED
 * @param workflowsFailed         runs in FAILED
 * @param workflowsSafeStopped    runs in SAFE_STOPPED
 * @param workflowsInFlight       runs that are not terminal yet
 * @param workflowSuccessRate     completed / (completed + failed + safe-stopped), null without terminal runs
 * @param agentRetryCount         agent/task attempts with attempt_no &gt; 1 (NOT optimistic-lock retries)
 * @param agentRetriesPerWorkflow agentRetryCount / workflowsTotal, null without runs
 * @param taskAttemptCount        all persisted agent/task attempts
 * @param fallbackInvocations     FALLBACK_INVOKED audit events
 * @param rollbackCount           snapshots whose rollback completed
 * @param rollbacksPerWorkflow    rollbackCount / workflowsTotal, null without runs
 * @param snapshotCount           persisted workspace snapshots
 * @param meanTimeToRecoverySeconds mean of (first successful attempt completion - first failed attempt completion)
 *                                over tasks that failed and later succeeded; null without recovery samples
 * @param recoverySamples         number of recovered tasks behind the MTTR value
 * @param meanWorkflowLatencySeconds mean of (completedAt - startedAt) over COMPLETED runs, null without samples
 * @param maxWorkflowLatencySeconds  maximum of the same population, null without samples
 * @param pendingApprovals        approvals still waiting for a human decision
 */
@Schema(name = "MetricsResponse", description = "Metrics derived from persisted orchestration data")
public record MetricsResponse(long workflowsTotal, long workflowsCompleted, long workflowsFailed,
		long workflowsSafeStopped, long workflowsInFlight, Double workflowSuccessRate, long agentRetryCount,
		Double agentRetriesPerWorkflow, long taskAttemptCount, long fallbackInvocations, long rollbackCount,
		Double rollbacksPerWorkflow, long snapshotCount, Double meanTimeToRecoverySeconds, long recoverySamples,
		Double meanWorkflowLatencySeconds, Double maxWorkflowLatencySeconds, long pendingApprovals) {
}
