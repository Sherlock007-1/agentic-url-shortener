-- Baseline migration for the orchestrator service.
-- Only establishes the dedicated schema; orchestration tables (requirements,
-- workflow runs/tasks, approvals, artifacts, audit events, ...) are added
-- incrementally by later migrations together with the features that need them.

CREATE SCHEMA IF NOT EXISTS orchestrator;
