# Testing

## Build command

From the repository root, with **Docker Desktop running**:

```
mvnw.cmd clean verify        # Windows
./mvnw clean verify          # Linux / macOS
```

Java 21 is required (`maven.compiler.release` is 21).

## Unit tests vs integration tests

**Unit tests** are plain JUnit 5 (+ Mockito, AssertJ). No Spring context, no database, milliseconds
each. They are used where the logic is a decision rather than an interaction: the ambiguity rules,
the collision classifier, the retry loop, URL validation, code generation, context serialisation,
failure classification, the change-policy guard, the graph template.

**Integration tests** are `@SpringBootTest` against **real PostgreSQL 16 via Testcontainers**. There
is no H2 and no in-memory substitute anywhere: Flyway migrations, JPA mappings, transaction
boundaries, unique constraints and JSON serialisation are all exercised as they run in production.

`AbstractPostgresIntegrationTest` (one per module) starts a singleton container per test JVM and
wires it in with `@DynamicPropertySource`. The container is started **unconditionally** — if Docker
is unavailable the build fails rather than silently skipping, so a green build always means the
database tests really ran. Nothing in the suite is `@Disabled` or `@Ignore`d; the expected result is
**0 skipped**.

Test-profile choices (`orchestrator-service/src/test/resources/application.properties`):

- the background scheduler is off, so tests drive workflows explicitly and one cached Spring context
  cannot interfere with another's workflows (`WorkflowSchedulerIntegrationTest` re-enables it on
  purpose);
- approval gates are off by default so the core tests can assert un-gated end-to-end execution;
  gating tests switch them on with `@TestPropertySource`;
- the snapshot workspace root points at the temp directory, so tests never write into the repository.

## What the important orchestrator tests prove

**Graph and scheduling**

| Test | Proves |
| --- | --- |
| `SdlcWorkflowGraphTemplateTest` | The template is a valid DAG: no cycles, no dangling edges, topological declaration order |
| `WorkflowGraphPersistenceIntegrationTest` | The graph is materialised into task and dependency rows; `createGraph` is idempotent |
| `WorkflowSchedulingRulesIntegrationTest` | A task is claimed only when **all** predecessors are `COMPLETED`; claiming is exactly-once |
| `WorkflowExecutionIntegrationTest` | End-to-end run, persisted statuses, cross-stage context, decisions and audit trail |
| `WorkflowSchedulerIntegrationTest` | The background poller can drive a workflow to completion on its own |
| `WorkflowFailureIntegrationTest` | A non-retryable failure fails the task and the run, with audit evidence |

**Parallelism proof** — `ParallelBranchExecutionIntegrationTest`

`tests`, `security` and `documentation` share `implementation` as their only predecessor. The test
asserts their persisted start/end intervals **overlap**, so it proves concurrency rather than merely
proving that three tasks eventually finished. It then asserts `validation` starts only after all
three are `COMPLETED` — the join.

**Governance and recovery**

| Test | Proves |
| --- | --- |
| `ApprovalGateIntegrationTest` | The run parks at `PRE_IMPLEMENTATION`/`FINAL`; approve resumes; reject ⇒ `SAFE_STOPPED`; approvals are per graph version |
| `ClarificationGateIntegrationTest` | The generic ask/answer mechanism, and that an answer can trigger a replan |
| `RetryAndFallbackIntegrationTest` | Only retryable failures are retried; the approved fallback runs once and its result is marked degraded |
| `RetryExhaustionIntegrationTest` | Attempts are bounded; exhaustion without a fallback ⇒ `SAFE_STOPPED`, never an infinite loop |
| `FailureClassifierTest` | Exactly which throwables count as retryable |
| `WorkflowBudgetIntegrationTest` | Exceeding the wall-clock budget safe-stops the run with audit evidence |
| `ChangePolicyGuardTest` | Path traversal, escaping the workspace, secret files and VCS metadata are refused |
| `WorkspaceSnapshotRollbackIntegrationTest` | Snapshot then rollback restores the workspace and records the outcome |
| `ReplanIntegrationTest` | Replanning creates v2 and leaves **every** v1 row untouched; both versions stay queryable |
| `MetricsIntegrationTest` | Success rate, retries, rollbacks, MTTR and latency are computed from persisted rows; empty samples are `null` |

**Scenario tests**

| Test | Proves |
| --- | --- |
| `AmbiguityDetectorTest` (14) | The known ambiguous phrasings trigger; actionable requirements — including the example clarification answer — do not |
| `KnownCapabilitiesTest` (7) | The "already exists" knowledge matches only the clarified security requirement, and both decisions explain themselves |
| `AmbiguousRequirementScenarioIntegrationTest` (8) | The full ambiguous scenario end to end (see below) |
| `ScenarioCatalogIntegrationTest` (5) | The catalog holds exactly the three scenarios, only the ambiguous one trips the gate, unknown keys 404, the API is published in OpenAPI |

`AmbiguousRequirementScenarioIntegrationTest` in detail:

1. the ambiguous requirement is **not** actioned autonomously — `AWAITING_CLARIFICATION`, and
   `codebase-analysis`, `planning` and `implementation` are all still `PENDING`;
2. the question and its rationale are persisted and readable over the API;
3. answering with `replan: true` creates graph v2, updates the requirement, and leaves v1 queryable;
4. the replan lineage names the previous and new requirement and the triggering clarification;
5. v2 runs to `COMPLETED` and records both "capability already exists" and "no code change required";
6. the same question is never asked twice;
7. an unambiguous requirement is never parked;
8. the scenario starts reproducibly through `POST /api/scenarios/{key}/start`.

## What the important URL shortener tests prove

| Test | Proves |
| --- | --- |
| `UrlValidatorTest` (12) | Only absolute `http`/`https` URIs with a host and within the length limit are accepted |
| `ShortCodeGeneratorTest` (2) | Codes have the configured length and alphabet |
| `ShortUrlServiceTest` (14) | Create/resolve/metadata/analytics/disable rules; a click is recorded on success and on **no** failure path |
| `ShortUrlServiceCollisionRetryTest` (5) | The bounded retry: 1 and 2 collisions succeed, 3 fail after exactly 3 attempts, unrelated failures are not retried |
| `ShortCodeCollisionsTest` (5) | Only a duplicate short code is classified as retryable |
| `ShortCodeCollisionRetryIntegrationTest` (4) | The **real** PostgreSQL unique constraint drives the retry; 3 collisions ⇒ `409` and no extra row |
| `ClickAnalyticsIntegrationTest` (10) | One redirect = one click; counts increment; unknown/expired/disabled record nothing; analytics survive a repository reload; no personal data is stored |
| `ShortUrlRepositoryIntegrationTest` (3) | Round-trip persistence, soft disable, and the unique constraint at the database level |
| `ShortUrlApiIntegrationTest` (13) | The whole HTTP surface, including that baseline behaviour is unchanged |

## Regression policy

Every test that existed before this increment still exists and still passes. Where the increment
changed a collaborator (the `ShortUrlService` constructor, `saveAndFlush` moving into
`ShortUrlWriter`), the existing tests were **updated, never deleted or weakened**, and new assertions
were added alongside them.

## Expected result

```
orchestrator-service   Tests run: 111, Failures: 0, Errors: 0, Skipped: 0
url-shortener-service  Tests run:  69, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
