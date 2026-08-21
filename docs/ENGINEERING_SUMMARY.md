# Engineering summary

## What was built

Two Spring Boot services in one Maven repository, backed by PostgreSQL:

- **`orchestrator-service` (8080)** — an agentic SDLC orchestrator. A requirement becomes a persisted
  DAG of agent tasks that executes with real parallelism, preserves context across stages, records
  decisions, stops at human gates, recovers from failures within bounded budgets, and can be
  replanned into a new graph version without destroying the old one.
- **`url-shortener-service` (8081)** — the product the orchestrator reasons about: create, redirect,
  expire, soft-disable, and (new) click analytics.

Both run locally with `docker compose up -d` and two `spring-boot:run` commands. Swagger is the
operator console. **No LLM credentials are required.**

## Important engineering decisions

**The graph is data, not code.** `SdlcWorkflowGraphTemplate` is only a template;
`WorkflowGraphService` materialises it into `workflow_tasks` and `task_dependencies` rows, and the
scheduler reads exclusively from those rows. This is the decision everything else rests on: it makes
state restartable, makes replanning possible without touching a code path, makes `GET /graph` show
precisely what the engine schedules from, and turns "which tasks may run now" into a query rather
than an `if` cascade.

**Agents are side-effect free with respect to orchestration state.** They receive a requirement and
their predecessors' outputs, and return output, a summary and decisions. They never set a status,
write audit rows or decide their own retries — `WorkflowTransitionService` and `TaskRunner` own that.
That is what makes agents replaceable and the engine testable.

**Governance is enforced in the claiming step, against persisted state.** Approval gates, the
clarification gate and the budget guard are all checked where tasks are claimed, so a restarted
orchestrator reaches the same decision. Autonomy boundaries are not advisory.

**Parallelism is proved, not asserted.** `tests`, `security` and `documentation` share one
predecessor and are claimed and dispatched together; `ParallelBranchExecutionIntegrationTest` asserts
their persisted execution intervals **overlap**. The join is the same all-predecessors-completed rule
applied to a node with three incoming edges — no barrier objects, no special-case code.

**Replanning versions, it does not mutate.** A replan derives graph version 2 and points the run at
it; version 1 and all of its tasks, decisions and audit events stay queryable. Approvals are recorded
per graph version, so autonomy is never inherited from a superseded plan.

**Deterministic agents by default, behind a provider-neutral seam.** `LlmClient` is the extension
point; `DeterministicLlmClient` needs no network and no key. The prototype is therefore reproducible
for any reviewer, and the tests assert orchestration behaviour rather than model prose.

**Transaction boundaries were treated as a correctness concern.** The short-code retry gives each
insert attempt its own `REQUIRES_NEW` transaction, because a PostgreSQL constraint violation aborts
the surrounding one. This is the kind of bug that passes mocked unit tests and fails in production —
so it is covered by an integration test against a real database.

## Controlled autonomy

The orchestrator is autonomous in the boring parts and explicitly not autonomous in the consequential
ones:

- it **stops before implementation** and **before declaring success**, and a human must approve;
- it **asks instead of guessing** when a requirement is not actionable, and records why;
- a **rejection is a safe stop**, not a failure and not a silent continue;
- **retries are bounded**, and a degraded fallback result is labelled as degraded and never presented
  as equivalent;
- **every budget breach ends in a controlled stop** with audit evidence, never in unbounded work;
- **every transition is persisted and auditable**, so the whole run can be reconstructed after the
  fact.

The design principle throughout: an autonomous system earns trust by being stoppable and explainable,
not by being unattended.

## Reliability and safety mechanisms

| Mechanism | Behaviour |
| --- | --- |
| Bounded retries | Only `RetryableAgentException` and attempt timeouts, max 3 attempts, every attempt persisted |
| Approved fallback | Runs once, output marked `[FALLBACK/DEGRADED]`, own decision record; none registered by default |
| Safe stop | Exhaustion, budget breach or human rejection ⇒ `SAFE_STOPPED` with a persisted reason |
| Budget guard | Attempts per task, wall-clock per run, timeout per attempt |
| Attempt isolation | A separate daemon pool bounds each attempt, so a hung agent cannot starve the workers |
| Policy guard | Mutations confined to the run's workspace; traversal, secrets and VCS metadata refused |
| Snapshot / rollback | Workspace snapshot before mutation, restore on request, outcome recorded |
| Audit + metrics | 30 event types; success rate, retries, rollbacks, MTTR and latency computed from rows |

## The three scenarios

| Scenario | Type | Outcome |
| --- | --- | --- |
| Click analytics | Greenfield | New Flyway migration, entity, service and endpoint. A successful redirect records one click; 404/410 record nothing. No IP, user agent or location is stored — privacy by design, enforced by the schema. |
| Collision-safe codes | Brownfield | The existing create path now retries a duplicate short code up to three times, each attempt in its own transaction. Unrelated integrity failures are never retried. The unique constraint stays the final boundary. |
| "Make shortened URLs more secure" | Ambiguous | The run parks in `AWAITING_CLARIFICATION` with a persisted question and rationale. A human answer creates graph v2 while v1 stays queryable. Under v2 the agents record that the clarified behaviour **already exists** and that **no code change is required**. |

The third scenario is the one worth reading twice. Its value is not a feature; it is the sequence
*clarify → inspect → discover it already exists → decline to write redundant code → keep the decision
traceable.* Deciding not to change code is a recorded outcome, not a skipped step.

## Validation

```
orchestrator-service   Tests run: 111, Failures: 0, Errors: 0, Skipped: 0
url-shortener-service  Tests run:  69, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Integration tests run against **real PostgreSQL 16 via Testcontainers** — no H2, no in-memory
substitute. Containers are started unconditionally, so a green build proves the database tests
actually ran. Nothing is disabled or skipped. Every test that existed before this increment still
exists and still passes; where a collaborator changed, existing tests were updated rather than
weakened.

## Major trade-offs

Single orchestrator instance with a JVM lock rather than distributed locking · deterministic runtime
agents and no external LLM configured · no authentication and no UI beyond Swagger · filesystem
snapshots rather than an artefact store · the policy guard is a guardrail, not a sandbox · ambiguity
detection and known-capability knowledge are curated allow-lists, not inference · analytics and
metrics are intentionally minimal · no Kafka, Redis, Kubernetes, Temporal or Camunda.

Each is a deliberate scoping decision, with the production alternative written down in
[RISKS_AND_TRADEOFFS.md](RISKS_AND_TRADEOFFS.md).

**GitHub Copilot was the development assistant; it is not the runtime agent system, and no agent in
this prototype edits the repository at runtime.** See [AI_USAGE.md](AI_USAGE.md).
