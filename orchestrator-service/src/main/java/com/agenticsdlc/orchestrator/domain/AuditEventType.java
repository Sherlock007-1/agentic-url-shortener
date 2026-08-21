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
	WORKFLOW_FAILED
}
