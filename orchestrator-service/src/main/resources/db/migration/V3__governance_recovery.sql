-- Governance, controlled autonomy and recovery.
--
-- Adds the persistence required for human approval gates, clarification gates,
-- bounded agent/task retries, fallback evidence, workspace snapshots/rollback and
-- replanning lineage. V1/V2 are never modified; existing tables are only extended
-- with additive, defaulted columns.

-- ---------------------------------------------------------------------------
-- Additive columns on the existing orchestration tables
-- ---------------------------------------------------------------------------

-- Why a controlled stop happened (SAFE_STOPPED), kept separate from error_message
-- which describes a plain failure.
ALTER TABLE workflow_runs ADD COLUMN safe_stop_reason TEXT;

-- Number of agent/task attempts (NOT optimistic-lock retries) executed for a task.
ALTER TABLE workflow_tasks ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------------
-- Human approval gates
-- ---------------------------------------------------------------------------

CREATE TABLE approvals (
    id              UUID         PRIMARY KEY,
    workflow_run_id UUID         NOT NULL REFERENCES workflow_runs (id),
    gate            VARCHAR(48)  NOT NULL,
    graph_version   INTEGER      NOT NULL,
    status          VARCHAR(24)  NOT NULL,
    requested_at    TIMESTAMPTZ  NOT NULL,
    resolved_at     TIMESTAMPTZ,
    reviewer        VARCHAR(128),
    comment         TEXT,
    version         BIGINT       NOT NULL DEFAULT 0,
    -- One gate decision per graph version: a replan must be approved again.
    CONSTRAINT uk_approval_gate_per_version UNIQUE (workflow_run_id, gate, graph_version)
);

CREATE INDEX idx_approvals_run_status ON approvals (workflow_run_id, status);

-- ---------------------------------------------------------------------------
-- Clarification gate
-- ---------------------------------------------------------------------------

CREATE TABLE clarification_requests (
    id              UUID         PRIMARY KEY,
    workflow_run_id UUID         NOT NULL REFERENCES workflow_runs (id),
    task_id         UUID         REFERENCES workflow_tasks (id),
    question        TEXT         NOT NULL,
    status          VARCHAR(24)  NOT NULL,
    answer          TEXT,
    answered_by     VARCHAR(128),
    requested_at    TIMESTAMPTZ  NOT NULL,
    resolved_at     TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_clarifications_run_status ON clarification_requests (workflow_run_id, status);

-- ---------------------------------------------------------------------------
-- Bounded retry / fallback evidence
-- ---------------------------------------------------------------------------

CREATE TABLE task_attempts (
    id              UUID         PRIMARY KEY,
    workflow_run_id UUID         NOT NULL REFERENCES workflow_runs (id),
    task_id         UUID         NOT NULL REFERENCES workflow_tasks (id),
    attempt_no      INTEGER      NOT NULL,
    kind            VARCHAR(16)  NOT NULL,
    outcome         VARCHAR(16)  NOT NULL,
    retryable       BOOLEAN      NOT NULL DEFAULT FALSE,
    error_message   TEXT,
    started_at      TIMESTAMPTZ  NOT NULL,
    completed_at    TIMESTAMPTZ,
    CONSTRAINT uk_task_attempt UNIQUE (task_id, attempt_no)
);

CREATE INDEX idx_task_attempts_run ON task_attempts (workflow_run_id);

-- ---------------------------------------------------------------------------
-- Workspace snapshots and rollback
-- ---------------------------------------------------------------------------

CREATE TABLE workspace_snapshots (
    id              UUID         PRIMARY KEY,
    workflow_run_id UUID         NOT NULL REFERENCES workflow_runs (id),
    task_id         UUID         REFERENCES workflow_tasks (id),
    label           VARCHAR(128) NOT NULL,
    location        TEXT         NOT NULL,
    file_count      INTEGER      NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    rollback_status VARCHAR(24),
    rolled_back_at  TIMESTAMPTZ,
    rollback_error  TEXT,
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_workspace_snapshots_run ON workspace_snapshots (workflow_run_id, created_at);

-- ---------------------------------------------------------------------------
-- Replanning lineage (graph version 2+)
-- ---------------------------------------------------------------------------

CREATE TABLE workflow_replans (
    id                   UUID         PRIMARY KEY,
    workflow_run_id      UUID         NOT NULL REFERENCES workflow_runs (id),
    from_graph_version   INTEGER      NOT NULL,
    to_graph_version     INTEGER      NOT NULL,
    reason               TEXT         NOT NULL,
    previous_requirement TEXT         NOT NULL,
    new_requirement      TEXT         NOT NULL,
    clarification_id     UUID         REFERENCES clarification_requests (id),
    created_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_replan_target UNIQUE (workflow_run_id, to_graph_version)
);

CREATE INDEX idx_workflow_replans_run ON workflow_replans (workflow_run_id, created_at);
