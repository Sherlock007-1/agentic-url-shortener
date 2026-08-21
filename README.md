# agentic-url-shortener

Agentic software engineering prototype: an SDLC orchestration service that drives a URL shortener
service through requirement understanding, decomposition, implementation, validation and
documentation under human oversight.

## Architecture (two modules)

| Module | Port | Purpose |
| --- | --- | --- |
| `orchestrator-service` | 8080 | Agentic SDLC orchestration (persisted workflow state, agents, governance, metrics) |
| `url-shortener-service` | 8081 | The product under development: short URL creation, redirect, expiration, analytics |

Both are independently runnable Spring Boot applications in a single Maven multi-module repository,
backed by one PostgreSQL instance using separate schemas (`orchestrator`, `shortener`).

## Technologies

- Java 21, Spring Boot 3.3.x
- Spring Web, Spring Data JPA, Bean Validation, Spring Boot Actuator
- PostgreSQL + Flyway migrations
- springdoc-openapi (Swagger UI)
- JUnit 5, Spring Boot Test, Testcontainers

## Local startup

1. Start PostgreSQL:

   ```
   docker compose up -d
   ```

2. Build from the repository root:

   ```
   mvnw.cmd clean verify        # Windows
   ./mvnw clean verify          # Linux / macOS
   ```

3. Run the services (separate terminals, or from STS):

   ```
   mvnw.cmd -pl orchestrator-service spring-boot:run
   mvnw.cmd -pl url-shortener-service spring-boot:run
   ```

4. Useful endpoints:

   - Orchestrator: http://localhost:8080/swagger-ui.html and http://localhost:8080/actuator/health
   - URL shortener: http://localhost:8081/swagger-ui.html and http://localhost:8081/actuator/health

Database settings default to local development values and can be overridden with environment
variables (`ORCHESTRATOR_DB_URL`, `ORCHESTRATOR_DB_USERNAME`, `ORCHESTRATOR_DB_PASSWORD`,
`SHORTENER_DB_URL`, `SHORTENER_DB_USERNAME`, `SHORTENER_DB_PASSWORD`). No credentials are committed.

## URL shortener API (baseline)

| Method | Path | Behaviour |
| --- | --- | --- |
| `POST` | `/api/urls` | Create a short URL (`400` for an invalid destination URL) |
| `GET` | `/{shortCode}` | `302` redirect for an active link, `404` unknown, `410` expired/disabled |
| `GET` | `/api/urls/{shortCode}` | Metadata lookup (`404` unknown) |
| `DELETE` | `/api/urls/{shortCode}` | Soft disable (`204`, `404` unknown); the row is kept so the code is never reused |

Destination URLs must be valid absolute `http`/`https` URIs with a host and at most 2048
characters; unsafe schemes such as `javascript:`, `file:` and `data:` are rejected. This is basic
input validation, not SSRF or open-redirect protection.

## Orchestrator API (core engine)

| Method | Path | Behaviour |
| --- | --- | --- |
| `POST` | `/api/requirements` | Creates requirement + workflow run + persisted graph version 1 (`READY`) |
| `POST` | `/api/workflows/{id}/start` | Starts execution (idempotent while `RUNNING`, `409` when not startable) |
| `GET` | `/api/workflows/{id}` | Workflow summary, status and safe-stop reason |
| `GET` | `/api/workflows/{id}/graph` | Graph of the current version (`?version=N` for an earlier one) |
| `GET` | `/api/workflows/{id}/graph/versions` | History of graph versions (replanning adds, never overwrites) |
| `GET` | `/api/workflows/{id}/tasks` | Task details of the current version including persisted context and attempt count |
| `GET` | `/api/workflows/{id}/decisions` | Recorded decisions (planning, architecture, approvals, fallback, replan) |
| `GET` | `/api/workflows/{id}/audit` | Ordered audit history |

The SDLC graph is persisted (not hard-coded control flow):
`requirement-analysis → codebase-analysis → planning → architecture → implementation`,
then `tests`, `security` and `documentation` in parallel, joined by `validation`.
Agents are provider-neutral (`LlmClient`); the bundled implementations are deterministic and
require no API credentials.

## Governance, controlled autonomy and recovery

| Method | Path | Behaviour |
| --- | --- | --- |
| `GET` | `/api/workflows/{id}/approvals` | Persisted approval gates and their status |
| `POST` | `/api/workflows/{id}/approvals/{approvalId}/approve` | Human approval; the run resumes from persisted state |
| `POST` | `/api/workflows/{id}/approvals/{approvalId}/reject` | Human rejection; the run is `SAFE_STOPPED` with a reason |
| `GET` | `/api/workflows/{id}/clarifications` | Questions the orchestrator asked instead of guessing |
| `POST` | `/api/workflows/{id}/clarifications/{clarificationId}/answer` | Answer (optionally `replan: true`) |
| `POST` | `/api/workflows/{id}/replan` | Creates the next graph version from a changed requirement |
| `GET` | `/api/workflows/{id}/replans` | Replan lineage (from/to version, reason, requirement change) |
| `GET` | `/api/workflows/{id}/snapshots` | Workspace snapshots and rollback outcome |
| `POST` | `/api/workflows/{id}/snapshots/{snapshotId}/rollback` | Restores the workspace from a snapshot |
| `GET` | `/api/metrics` | Metrics derived from persisted records |

Swagger is the operator console for this assessment: there is no authentication and no UI, and the
orchestrator never approves anything on its own.

**Workflow states**: `CREATED → PLANNING → READY → RUNNING`, plus `WAITING_FOR_APPROVAL`,
`AWAITING_CLARIFICATION`, `RETRYING`, `REPLANNING`, `ROLLING_BACK` and the terminal states
`COMPLETED`, `FAILED` and `SAFE_STOPPED`. Task states add `RETRYING` and `WAITING_FOR_APPROVAL`.
Every transition is persisted, validated and audited.

**Approval gates** (`orchestrator.governance.approval-gates`): `PRE_IMPLEMENTATION` blocks the
implementation task after architecture completes, `FINAL` blocks the transition to `COMPLETED`
after validation. Approvals are per graph version, so a replan must be approved again.

**Bounded retries**: only failures classified retryable (`RetryableAgentException` or an attempt
timeout) are repeated, at most `orchestrator.governance.max-task-attempts` times (default 3). Every
attempt is persisted in `task_attempts`. This is *not* the engine's optimistic-locking retry, which
is a concurrency mechanism and is never counted as an agent retry.

**Fallback and safe stop**: after the retries are exhausted an explicitly approved `AgentFallback`
may run once. Its result is marked `[FALLBACK/DEGRADED]`, gets its own decision record and is never
presented as an equivalent successful primary run. Without a fallback, or if it fails, the run is
`SAFE_STOPPED` with an audited reason. No fallback is registered by default.

**Autonomy boundaries**: attempts per task, wall-clock duration per workflow
(`max-workflow-duration`) and execution time per attempt (`task-timeout`). Exceeding one leads to a
controlled safe stop, never to unbounded work. Token accounting is out of scope for deterministic
agents.

**Change policy**: `ChangePolicyGuard` restricts file mutations to
`{workspace-root}/{workflowId}/workspace`, rejects path traversal and refuses credential/secret
files and VCS metadata. It is an application-level guardrail, **not** a security sandbox.

**Rollback**: `runs/{workflowId}/workspace` can be copied into `runs/{workflowId}/snapshots/{id}`
before a mutating stage and restored from it. Plain directory copy - no Git, no artefact store, no
distributed storage.

**Metrics formulas**: success rate = `COMPLETED / (COMPLETED + FAILED + SAFE_STOPPED)`; agent
retries = attempts with `attempt_no > 1`; rollbacks = snapshots with `rollback_status = COMPLETED`;
MTTR = mean of (first successful attempt − first failed attempt) per recovered task; latency =
mean/max of `completedAt − startedAt` over completed runs. Metrics without samples are `null`, never
invented.

## Status

Implementation is intentionally incremental. Implemented so far: the URL shortener baseline
(creation, redirect, metadata, soft disable, expiration), the orchestration core (persisted DAG,
parallel execution with join, agent abstraction, cross-stage context, decision lineage, audit trail)
and the governance/recovery layer (approval and clarification gates, bounded retries, approved
fallback, safe stop, autonomy budgets, change-policy guardrail, workspace snapshot/rollback,
replanning into new graph versions and metrics).

Deferred on purpose: the greenfield click-analytics, brownfield short-code collision and ambiguous
"security" scenarios, source-code mutating agents, a real external LLM provider, UI, authentication
and multi-instance orchestration.