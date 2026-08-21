# Risks and trade-offs

Everything below is a deliberate choice for a take-home prototype, not an oversight. Each entry says
what was done, why, and what production would need instead.

## Single orchestrator instance

**What.** Exactly one orchestrator process is assumed.

**Why.** A second instance adds no capability a reviewer can observe locally, but it does add
significant operational surface.

**Production.** Leader election or partitioned ownership of runs, plus the change below.

## JVM lock, not distributed locking

**What.** Mutual exclusion while claiming tasks is a per-workflow `ReentrantLock` inside
`WorkflowEngine`, backed by JPA `@Version` optimistic locking.

**Why.** Correct and simple for one process, and it keeps the scheduling logic readable.

**Mitigation already in place.** Scheduling reads from persisted rows and claims tasks inside a
transaction, so state is never only in memory.

**Production.** Replace the JVM lock with `SELECT ... FOR UPDATE SKIP LOCKED` over eligible tasks, or
a lease in Redis/ZooKeeper. Because the scheduler already reads from the database, this is a
contained change rather than a redesign.

## Deterministic runtime agents by default

**What.** The bundled agents call `DeterministicLlmClient`, which does no network I/O and returns
stable text for the same inputs.

**Why.** Reproducibility. Tests assert orchestration behaviour, not model prose. The whole prototype
runs on any machine with a JDK and Docker, at zero cost, with no account.

**Honest limitation.** `DeterministicLlmClient` is a demo implementation. It is **not** a simulation
of a model and is never presented as one; it produces structured placeholder text.

**Production.** Add one `LlmClient` bean for a real provider. The engine, the graph, the gates and
the agent contracts are unchanged.

## No runtime external LLM configured

**What.** No API key, endpoint or model name is read at runtime. Nothing to configure.

**Why.** A reviewer must be able to run everything without credentials, and no secret should ever be
near this repository.

**Production.** Provider credentials from a secret manager, plus per-run token/cost budgets fed into
`BudgetGuard` (which today bounds attempts, wall-clock duration and per-attempt timeout — token
accounting is not applicable to deterministic agents and is deliberately not faked).

## GitHub Copilot is not the runtime agent system

**What.** Copilot was the development assistant for this repository. It is not a dependency, not a
component and not invoked at runtime.

**Why.** Conflating the two would be misleading. See [AI_USAGE.md](AI_USAGE.md).

## No agent mutates the repository

**What.** The implementation agent describes a change; it does not edit source files. Scenario code
changes are the change produced and validated during development.

**Why.** A demo that rewrote its own source would be less safe and far less reviewable, and any
"evidence" it produced would be unverifiable.

**Production.** A sandboxed workspace, a real VCS integration, branch/PR creation, and a policy layer
considerably stronger than `ChangePolicyGuard`.

## No authentication, no UI

**What.** Swagger is the operator console. Reviewer names on approvals are free text, persisted
as-is.

**Why.** Authentication would add setup friction without demonstrating anything about orchestration.

**Production.** OIDC, per-role authorisation on the governance endpoints (approving a gate is a
privileged action), and an audit trail tied to authenticated identities rather than typed names.

## Simple filesystem snapshots

**What.** `runs/{workflowId}/workspace` is copied to `runs/{workflowId}/snapshots/{id}` and restored
by directory copy.

**Why.** It demonstrates snapshot/rollback semantics and outcome recording without an artefact store.

**Limitations.** Not atomic across a crash mid-copy, not deduplicated, local disk only, no retention
policy.

**Production.** Git-based checkpoints or content-addressed object storage, with retention and
integrity verification.

## The policy guard is not a sandbox

**What.** `ChangePolicyGuard` confines mutations to the run's workspace, rejects path traversal, and
refuses credential/secret files and VCS metadata.

**Honest limitation.** It is an **application-level guardrail**. It constrains well-behaved code. It
does not contain hostile code and offers no protection at the process, filesystem or network level.

**Production.** Container or microVM isolation, a read-only base filesystem, seccomp/AppArmor, no
outbound network by default, and resource quotas.

## No Kafka, Redis, Kubernetes, Temporal or Camunda

**What.** In-process scheduling on a bounded thread pool, with PostgreSQL as the only state store.

**Why.** A workflow engine would *replace* the thing being assessed; a broker and an orchestrator
platform would add infrastructure without adding demonstrated capability.

**Production.** For durable timers, fan-out at scale and cross-service sagas, a purpose-built engine
(Temporal) is the right answer, and the persisted-DAG model here maps onto it cleanly.

## Analytics are intentionally minimal

**What.** `click_events(id, short_url_id, clicked_at, referrer)` plus totals, last click and a
10-entry recent window.

**Why.** Privacy by design: no IP address, user agent, device fingerprint or geo-location, because
none of it is needed for click counts. Not collecting data removes a class of personal-data handling
outright.

**Limitations.** No unique-visitor counting, no time bucketing, no per-country breakdown, no
retention policy, no dashboard. Every redirect writes one row synchronously.

**Production.** Asynchronous ingestion, roll-up tables or a time-series store, an explicit retention
policy, and a documented lawful basis for anything beyond a counter.

## Metrics suit prototype scale

**What.** `GET /api/metrics` computes success rate, agent retries, rollbacks, MTTR and E2E latency by
querying persisted rows on demand. Metrics with no samples return `null`, never a fabricated `0`.

**Limitations.** Full scans, no caching, no time windows, no percentiles, no Prometheus export.

**Production.** Micrometer counters/timers exported to Prometheus, with the database used for
lineage rather than for aggregation.

## Ambiguity detection is an allow-list

**What.** `AmbiguityDetector` matches a short, explicit list of known-ambiguous phrasings.

**Why.** A general "is this requirement clear?" classifier is beyond a deterministic agent, and
treating every requirement as suspect would turn the clarification gate into a permanent stop.

**Honest limitation.** It will not recognise an ambiguity that is not on the list.

**Production.** Model-assisted ambiguity analysis with a confidence threshold, still gated by the
same persisted clarification mechanism — the mechanism is the durable part, the detector is not.

## `KnownCapabilities` is configured, not discovered

**What.** The "this already exists" knowledge used by codebase analysis is a hard-coded entry with
pointers to the implementing classes and their tests.

**Why.** Real repository inspection is a project in itself and is out of scope.

**Honest limitation.** If the shortener's validation were removed, the agent would still claim it
exists. The claim is only as good as the list.

**Production.** Static analysis, symbol indexing or retrieval over the actual source tree, with the
decision citing the specific code it found.

## Testing trade-offs

**What.** Integration tests use one singleton PostgreSQL container per test JVM, and the background
scheduler is disabled in the test profile.

**Why.** A shared container keeps the Spring context cacheable and the suite fast; disabling the
poller stops one cached context from driving another context's workflows.

**Limitations.** Test classes must clean the schema themselves (`TestDatabase.clean`), and the suite
does not exercise multi-instance contention — because the prototype does not support it.
