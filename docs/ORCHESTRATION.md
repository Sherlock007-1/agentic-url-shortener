# Orchestration

How the orchestrator actually runs a requirement, in plain terms.

## The DAG

The SDLC plan is a directed acyclic graph, declared once in `SdlcWorkflowGraphTemplate` and then
**persisted per workflow run** as `workflow_tasks` + `task_dependencies` rows:

```
requirement-analysis -> codebase-analysis -> planning -> architecture -> implementation
                                                                             |
                                             +-------------+----------------+
                                             v             v                v
                                           tests       security     documentation
                                             +-------------+----------------+
                                                           v
                                                       validation   (join)
```

The scheduler never reads the template. It reads the rows. `GET /api/workflows/{id}/graph` returns
exactly what it schedules from.

## Sequential paths

`requirement-analysis → codebase-analysis → planning → architecture → implementation` is a chain of
single-predecessor edges. A task is only eligible when **all** of its predecessors are `COMPLETED`,
so the ordering is a property of the data, not of the code.

## Parallel fan-out

`tests`, `security` and `documentation` all depend on `implementation` and on nothing else. When
`implementation` completes, `claimEligibleTasks` finds all three eligible in the same pass, claims
all three in one transaction, and submits all three to the worker pool. They run concurrently on
different threads.

Proof: `ParallelBranchExecutionIntegrationTest` asserts that the three branch tasks have
**overlapping** persisted start/end intervals — not that they merely all finished.

## Synchronization (the join)

`validation` depends on all three branches. It is promoted only when every predecessor is
`COMPLETED`. There is no barrier object, no countdown latch and no "wait for branch" code path: the
join is the same all-predecessors-completed rule applied to a node with three incoming edges.

## Context propagation

When a task is claimed, the outputs of its completed predecessors are snapshotted onto the task row
(`input_context`) and handed to the agent as `AgentContext.upstreamOutputs`, keyed by task key.

- Agents declare what they need (`requiredUpstreamKeys`) and fail fast if the engine ever scheduled
  them too early.
- The join task receives the outputs of all three branches, so validation genuinely sees the test,
  security and documentation results.
- Context is persisted, so `GET /api/workflows/{id}/tasks` shows what each stage was actually given —
  the cross-stage handover is auditable, not implicit.

## Decisions

Agents return `AgentDecision`s; governance actions record their own. Everything lands in `decisions`
and is exposed at `GET /api/workflows/{id}/decisions`. Recorded decision types include:

| Type | Recorded by | Meaning |
| --- | --- | --- |
| `PLANNING`, `ARCHITECTURE` | agents | why the work was decomposed that way |
| `CODEBASE_ANALYSIS` | codebase analysis | e.g. "requested capability already exists", with evidence |
| `IMPLEMENTATION` | implementation | e.g. "no code change required", with justification |
| `APPROVAL` | `ApprovalService` | who approved or rejected which gate, and why |
| `CLARIFICATION` | `ClarificationGateService` | why the requirement could not be actioned as written |
| `REPLAN` | `ReplanService` | old vs new requirement, from/to graph version |
| `FALLBACK` | `TaskRunner` | that a degraded result was accepted, and that it is not equivalent |

## Approval gates

Configured by `orchestrator.governance.approval-gates` (default `PRE_IMPLEMENTATION,FINAL`).

- `PRE_IMPLEMENTATION` blocks the `implementation` task after `architecture` completes.
- `FINAL` blocks the transition to `COMPLETED` after `validation` completes.

`ApprovalService.requireGate` only ever *requests* an approval and parks the run
(`WAITING_FOR_APPROVAL`). The orchestrator never approves anything itself. A human resolves it via
Swagger:

- **approve** → decision + audit event, tasks released, run returns to `RUNNING`;
- **reject** → decision + audit event, run is `SAFE_STOPPED` with a reason. A rejection is not a
  failure and not a silent continue — it is an explicit, auditable stop.

Approvals are recorded **per graph version**, so a replan must be approved again. Autonomy is not
inherited from a previous plan.

## Clarification

The generic mechanism is `ClarificationService`: `ask` parks the run in `AWAITING_CLARIFICATION`
with a persisted question; `answer` persists the answer and un-parks the run once nothing is
pending. The caller decides whether the answer should also trigger a replan.

Triggering is separate and narrow. `AmbiguityDetector` holds a short, explicit list of requirement
phrasings known to be ambiguous; `ClarificationGateService` checks it in front of `codebase-analysis`
— i.e. after the requirement has been read, before anything is built on a guess.

Deliberate properties:

- **not global** — an unambiguous requirement is never parked
  (`AmbiguityDetectorTest`, `AmbiguousRequirementScenarioIntegrationTest`);
- **asked at most once per run** — once a human has answered, the run proceeds under that answer, so
  the gate can never loop;
- **explained** — a `CLARIFICATION` decision records *why* the orchestrator stopped.

## Bounded retries

Only failures classified as retryable (`RetryableAgentException`, or an attempt timeout) are
repeated, at most `orchestrator.governance.max-task-attempts` times (default 3). Every attempt is a
row in `task_attempts`. A non-retryable failure fails the task immediately — no loop, no fallback.

This is **not** the engine's optimistic-locking retry, which is a concurrency mechanism and is never
counted as an agent retry. It is also unrelated to the URL shortener's short-code retry, which is a
business rule in a different service.

## Fallback

After the retries are exhausted, one **explicitly registered** `AgentFallback` may run once. Its
result is prefixed `[FALLBACK/DEGRADED]`, gets its own `FALLBACK` decision recording that it is a
reduced-confidence result, and is never presented as equivalent to a successful primary run. No
fallback is registered by default.

## Safe stop

If a task exhausts its attempts and there is no fallback (or the fallback also fails), if a budget is
exceeded, or if a human rejects a gate, the run becomes `SAFE_STOPPED` with a persisted reason and an
audit event. The system never keeps grinding and never fails silently.

## Budget guard

Three autonomy boundaries, all checked against persisted state:

| Property | Meaning |
| --- | --- |
| `max-task-attempts` | attempts per task (retries = attempts − 1) |
| `max-workflow-duration` | wall-clock budget per run, from its start |
| `task-timeout` | execution time per single agent attempt |

Exceeding any of them leads to a controlled safe stop with audit evidence, never to unbounded work.
Token accounting is not applicable to deterministic agents and is not faked.

## Policy guard

`ChangePolicyGuard` restricts file mutations to `{workspace-root}/{workflowId}/workspace`, rejects
path traversal, and refuses credential/secret files and VCS metadata. It is an **application-level
guardrail, not a security sandbox** — it constrains well-behaved code, it does not contain hostile
code.

## Snapshot and rollback

`runs/{workflowId}/workspace` can be copied into `runs/{workflowId}/snapshots/{id}` before a mutating
stage and restored from it via `POST /api/workflows/{id}/snapshots/{snapshotId}/rollback`. The
snapshot row records the rollback outcome. Plain directory copy — no Git, no artefact store, no
distributed storage.

## Replanning and version history

`POST /api/workflows/{id}/replan` (or answering a clarification with `replan: true`) creates the
**next graph version** from the template and points the run at it:

- version 1 and every task, decision and audit event belonging to it stay persisted and queryable
  (`GET /api/workflows/{id}/graph?version=1`, `GET /api/workflows/{id}/graph/versions`);
- a `workflow_replans` row records from/to version, the reason, the previous and new requirement,
  and the clarification that triggered it;
- a `REPLAN` decision states in words what changed and that the old version is preserved;
- the run returns to `READY`, so a human must start it and the new version's gates must be approved
  again.

This is versioning, not mutation. There is deliberately no graph-diff engine: deterministic
regeneration plus recorded lineage is enough to explain what changed and why.

## Audit

`audit_events` is the ordered history of the run: workflow created/started/completed/failed, graph
created, task ready/started/completed/failed, decision recorded, approval requested/granted/rejected,
clarification requested/answered, attempt failed, retry scheduled, task recovered, fallback
invoked/succeeded/failed, snapshot created, rollback started/completed/failed, budget exceeded, safe
stop, replan started/completed. Exposed at `GET /api/workflows/{id}/audit`.

## Metrics

`GET /api/metrics`, computed on demand from persisted rows — nothing is estimated or invented:

| Metric | Formula |
| --- | --- |
| Success rate | `COMPLETED / (COMPLETED + FAILED + SAFE_STOPPED)` |
| Agent retries | attempts with `attempt_no > 1` |
| Rollbacks | snapshots with `rollback_status = COMPLETED` |
| MTTR | mean of (first successful attempt − first failed attempt) per recovered task |
| E2E latency | mean/max of `completedAt − startedAt` over completed runs |

Metrics with no samples are returned as `null`, never as `0`.
