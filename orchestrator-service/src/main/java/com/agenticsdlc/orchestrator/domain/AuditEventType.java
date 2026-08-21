package com.agenticsdlc.orchestrator.domain;

/** Types of auditable orchestration events. */
public enum AuditEventType {
	WORKFLOW_CREATED,
	GRAPH_CREATED,
	WORKFLOW_STARTED,
	TASK_READY,
	TASK_STARTED,
	TASK_COMPLETED,
	TASK_FAILED,
	DECISION_RECORDED,
	WORKFLOW_COMPLETED,
	WORKFLOW_FAILED,

	// --- governance -------------------------------------------------------
	APPROVAL_REQUESTED,
	APPROVAL_GRANTED,
	APPROVAL_REJECTED,
	CLARIFICATION_REQUESTED,
	CLARIFICATION_ANSWERED,
	POLICY_REJECTED,

	// --- recovery ---------------------------------------------------------
	/** One bounded agent/task attempt failed (not an optimistic-lock retry). */
	TASK_ATTEMPT_FAILED,
	TASK_RETRY_SCHEDULED,
	TASK_RECOVERED,
	FALLBACK_INVOKED,
	FALLBACK_SUCCEEDED,
	FALLBACK_FAILED,
	SNAPSHOT_CREATED,
	ROLLBACK_STARTED,
	ROLLBACK_COMPLETED,
	ROLLBACK_FAILED,

	// --- autonomy boundaries / replanning ---------------------------------
	BUDGET_EXCEEDED,
	WORKFLOW_SAFE_STOPPED,
	REPLAN_STARTED,
	REPLAN_COMPLETED
}