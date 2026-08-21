# Architecture

## Why two services

| Service | Port | Schema | Role |
| --- | --- | --- | --- |
| `orchestrator-service` | 8080 | `orchestrator` | Runs the agentic SDLC workflow |
| `url-shortener-service` | 8081 | `shortener` | The product the workflow is *about* |

They are separate applications on purpose. The orchestrator must be able to reason about, plan for
and validate a system that is not itself — if the shortener were a package inside the orchestrator,
"codebase analysis" and "implementation" would be indistinguishable from ordinary method calls, and
the boundary that makes the prototype interesting would disappear.

They share one PostgreSQL instance but not one schema. Flyway owns both schemas independently
(`orchestrator/db/migration`, `shortener/db/migration`), so neither service can migrate or corrupt
the other's tables. One instance keeps local setup to a single `docker compose up -d`.

## Persistence: an explicit, persisted DAG

The workflow graph is **data**, not control flow.

```
requirements          the raw requirement text (updated by a replan)
workflow_runs         status, current graph version, timings, safe-stop reason
workflow_graph_versions  one row per plan version of a run
workflow_tasks        one row per node, bound to a graph version
task_dependencies     one row per edge
agent_executions      one row per agent invocation with input/output context
task_attempts         one row per attempt (primary or fallback), for retry/MTTR metrics
decisions             decision lineage
audit_events          ordered history of everything that happened
approvals             human approval gates, per graph version
clarification_requests questions asked and answers received
workflow_replans      from/to version, reason, requirement change, triggering clarification
workspace_snapshots   snapshot + rollback outcome
```

`SdlcWorkflowGraphTemplate` is only a *template*: `WorkflowGraphService.createGraph` materialises it
into `workflow_tasks` + `task_dependencies` rows for a specific run and version. The scheduler then
reads exclusively from those rows. Consequences that matter:

- restarting the orchestrator loses nothing — state is in the database, not in a thread;
- a replan can produce a different graph without changing any Java code path;
- "which tasks may run now" is a query over persisted edges and statuses, not an `if` cascade.

## Agent abstraction

```java
public interface Agent {
    AgentType type();
    AgentResult execute(AgentContext context);
}
```

- `AgentContext` gives an agent the requirement plus the outputs of its completed predecessors
  (`upstreamOutputs`), and nothing else.
- `AgentResult` returns output text (context for downstream tasks), a summary for the audit trail,
  and decisions to persist.
- Agents are **side-effect free with respect to orchestration state**. They never set a status,
  never write audit rows, never decide their own retries. `WorkflowTransitionService` owns all of
  that. That separation is what makes the engine testable and the agents replaceable.

`AgentRegistry` resolves the agent for an `AgentType`; `FallbackRegistry` optionally provides one
explicitly approved fallback per type.

## Deterministic agents vs a real LLM

Agents do not call a model directly. They call `LlmClient`:

```java
public interface LlmClient {
    String complete(String role, String instruction, Map<String, String> context);
}
```

The bundled `DeterministicLlmClient` performs no network call and needs no credentials: the same
role, instruction and context always produce the same text.

This is a deliberate, disclosed choice:

- the prototype is **reproducible** — tests assert on orchestration behaviour, not on model output;
- it runs on any machine with Docker and a JDK, with no API key and no cost;
- swapping in a real provider means adding one `LlmClient` bean. The engine, the graph, the gates
  and the agents' contracts do not change.

`DeterministicLlmClient` is a demo/test implementation. It is **not** a simulation of a model and is
never presented as one.

`KnownCapabilities` is the one place where an agent has domain knowledge about the target codebase.
It is a short, human-configured list, used to record "this already exists" as a decision. A real
system would inspect the repository; that is explicitly out of scope.

## Scheduling and the thread pool

`WorkflowEngine.advance(workflowId)`:

1. takes a per-workflow `ReentrantLock` (the lock instance is stable for the run's lifetime);
2. asks `WorkflowTransitionService.claimEligibleTasks` for every task whose predecessors are all
   `COMPLETED` and whose gates allow it, marking them `RUNNING` inside one transaction;
3. releases the lock and submits each claimed task to a bounded `ThreadPoolTaskExecutor`;
4. when a task finishes, `advance` is called again, so completing a task unblocks its successors.

Claiming under the lock is what guarantees a task is dispatched exactly once. Submitting outside the
lock is what allows independent branches to run genuinely in parallel.

Two separate pools:

- `orchestratorTaskExecutor` — bounded worker pool for tasks (`CallerRunsPolicy`, so a full queue
  slows the caller instead of dropping work);
- `agentTimeoutExecutor` — daemon threads used only to bound a single agent attempt with a timeout,
  so a hung agent can never consume the workers that drive the rest of the graph.

`WorkflowScheduler` is a background poller that nudges runnable workflows forward; it is a safety
net, not the primary path (completion chaining is). Tests disable it and drive workflows explicitly
so one cached Spring context cannot interfere with another's workflows.

## Governance layer

Governance is enforced **inside the claiming step**, against persisted state — so a restarted
orchestrator makes the same decision:

- `BudgetGuard` — wall-clock budget per run, attempts per task, timeout per attempt;
- `ApprovalService` / `ApprovalGates` — human approval gates, per graph version;
- `ClarificationGateService` / `AmbiguityDetector` — stop and ask instead of guessing;
- `SafeStopService` — controlled stop with an audited reason, never a silent continue;
- `ChangePolicyGuard` / `PolicyGuardService` — restricts file mutations to the run's workspace;
- `WorkspaceSnapshotService` — snapshot before a mutating stage, restore on request;
- `ReplanService` — new graph version, old version preserved;
- `AuditService` — every transition recorded.

See [ORCHESTRATION.md](ORCHESTRATION.md).

## Why single-instance is intentional

The orchestrator assumes exactly one running instance. Mutual exclusion is a per-workflow JVM
`ReentrantLock`, backed by JPA `@Version` optimistic locking on the entities.

This is a deliberate trade-off, not an oversight:

- the assessment is a local, runnable prototype — a second instance adds no demonstrable capability;
- distributed locking (Redis/ZooKeeper) or a workflow engine (Temporal, Camunda) would add a large
  operational surface that would obscure, not clarify, the orchestration design;
- the design does not *depend* on single-instance semantics for correctness of its data model:
  state is fully persisted, tasks are claimed transactionally, and entities carry version columns.
  Moving to multiple instances means replacing the JVM lock with `SELECT ... FOR UPDATE SKIP LOCKED`
  or a distributed lease — a contained change, precisely because scheduling reads from the database.

This is stated plainly rather than hidden, and is repeated in
[RISKS_AND_TRADEOFFS.md](RISKS_AND_TRADEOFFS.md).
