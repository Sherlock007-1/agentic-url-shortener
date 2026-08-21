# Traceability

Each capability maps to concrete code, an endpoint, a test and a document. Paths are relative to the
repository root; `o/` = `orchestrator-service/src/main/java/com/agenticsdlc/orchestrator`,
`u/` = `url-shortener-service/src/main/java/com/agenticsdlc/shortener`.

| # | Capability | Implementation | API | Test evidence | Docs |
| --- | --- | --- | --- | --- | --- |
| 1 | Requirement understanding | `o/domain/Requirement.java`, `o/engine/WorkflowService.createWorkflow`, `o/agent/deterministic/RequirementAnalysisAgent.java` | `POST /api/requirements` | `WorkflowExecutionIntegrationTest`, `OrchestratorApiIntegrationTest` | ORCHESTRATION |
| 2 | Decomposition into stages | `o/graph/SdlcWorkflowGraphTemplate.java`, `o/graph/TaskDefinition.java`, `o/agent/deterministic/PlanningAgent.java` | `GET /api/workflows/{id}/tasks` | `SdlcWorkflowGraphTemplateTest`, `WorkflowGraphPersistenceIntegrationTest` | ORCHESTRATION |
| 3 | Explicit persisted DAG | `o/engine/WorkflowGraphService.java`, `o/domain/WorkflowTask.java`, `o/domain/TaskDependency.java`, `V2__orchestration_core.sql` | `GET /api/workflows/{id}/graph` | `WorkflowGraphPersistenceIntegrationTest` (materialised + idempotent) | ARCHITECTURE, ORCHESTRATION |
| 4 | Sequential execution | `o/engine/WorkflowTransitionService.claimEligibleTasks` (all predecessors `COMPLETED`) | `GET /api/workflows/{id}/tasks` | `WorkflowSchedulingRulesIntegrationTest`, `WorkflowExecutionIntegrationTest` | ORCHESTRATION |
| 5 | Parallel execution | `o/engine/WorkflowEngine.advance` + `orchestratorTaskExecutor` (`o/config/OrchestratorConfiguration.java`) | `GET /api/workflows/{id}/tasks` (overlapping timings) | **`ParallelBranchExecutionIntegrationTest`** — asserts overlapping start/end intervals | ORCHESTRATION, TESTING |
| 6 | Synchronization / join | Multi-edge `validation` node + the same all-predecessors rule | `GET /api/workflows/{id}/graph` | `ParallelBranchExecutionIntegrationTest`, `WorkflowSchedulingRulesIntegrationTest` | ORCHESTRATION |
| 7 | Persisted state / restartability | `o/domain/WorkflowRun.java`, `o/domain/WorkflowStatus.java`, `o/repository/*`, Flyway V1–V3 | `GET /api/workflows/{id}` | `WorkflowExecutionIntegrationTest`, `WorkflowSchedulerIntegrationTest` | ARCHITECTURE |
| 8 | Context preservation across stages | `o/agent/AgentContext.java`, `o/engine/ContextSerializer.java`, `input_context` / `output_context` columns | `GET /api/workflows/{id}/tasks` | `ContextSerializerTest`, `WorkflowExecutionIntegrationTest`, `DeterministicAgentTest` | ORCHESTRATION |
| 9 | Decision lineage | `o/domain/Decision.java`, `o/agent/AgentDecision.java`, `o/engine/WorkflowTransitionService.completeTask` | `GET /api/workflows/{id}/decisions` | `WorkflowExecutionIntegrationTest`, `ReplanIntegrationTest`, `AmbiguousRequirementScenarioIntegrationTest` | ORCHESTRATION |
| 10 | Human approval gates | `o/governance/ApprovalService.java`, `o/governance/ApprovalGates.java`, `o/domain/Approval.java` | `GET /api/workflows/{id}/approvals`, `POST .../approve` \| `/reject` | **`ApprovalGateIntegrationTest`** — parks, resumes, rejects to `SAFE_STOPPED`, per graph version | ORCHESTRATION |
| 11 | Clarification (ask, don't guess) | `o/governance/ClarificationService.java`, **`o/governance/ClarificationGateService.java`**, **`o/governance/AmbiguityDetector.java`** | `GET /api/workflows/{id}/clarifications`, `POST .../answer` | `ClarificationGateIntegrationTest`, **`AmbiguityDetectorTest`**, **`AmbiguousRequirementScenarioIntegrationTest`** | ORCHESTRATION, SCENARIOS |
| 12 | Bounded retry | `o/engine/TaskRunner.run`, `o/governance/FailureClassifier.java`, `o/governance/RecoveryService.java`, `o/domain/TaskAttempt.java` | `GET /api/workflows/{id}/tasks` (attempt count), `/audit` | `RetryAndFallbackIntegrationTest`, `RetryExhaustionIntegrationTest`, `FailureClassifierTest` | ORCHESTRATION |
| 13 | Fallback (degraded, labelled) | `o/agent/AgentFallback.java`, `o/agent/FallbackRegistry.java`, `o/engine/TaskRunner.exhausted` | `GET /api/workflows/{id}/decisions` (`FALLBACK`) | `RetryAndFallbackIntegrationTest` | ORCHESTRATION |
| 14 | Rollback | `o/governance/WorkspaceSnapshotService.java`, `o/governance/WorkspaceService.java`, `o/domain/WorkspaceSnapshot.java` | `GET /api/workflows/{id}/snapshots`, `POST .../rollback` | `WorkspaceSnapshotRollbackIntegrationTest` | ORCHESTRATION, RISKS |
| 15 | Safe stop | `o/governance/SafeStopService.java`, `WorkflowStatus.SAFE_STOPPED` | `GET /api/workflows/{id}` (`safeStopReason`) | `RetryExhaustionIntegrationTest`, `ApprovalGateIntegrationTest`, `WorkflowBudgetIntegrationTest` | ORCHESTRATION |
| 16 | Autonomy budgets | `o/governance/BudgetGuard.java`, `o/config/GovernanceProperties.java` | `GET /api/workflows/{id}/audit` (`BUDGET_EXCEEDED`) | `WorkflowBudgetIntegrationTest` | ORCHESTRATION, RISKS |
| 17 | Policy guardrails | `o/governance/ChangePolicyGuard.java`, `o/governance/PolicyGuardService.java`, `o/governance/PolicyViolationException.java` | HTTP 403 via `OrchestratorExceptionHandler` | `ChangePolicyGuardTest` (traversal, escape, secrets, VCS metadata) | ORCHESTRATION, RISKS |
| 18 | Audit observability | `o/engine/AuditService.java`, `o/domain/AuditEvent.java`, `o/domain/AuditEventType.java` (30 types) | `GET /api/workflows/{id}/audit` | Asserted in nearly every integration test | ORCHESTRATION |
| 19 | Success / retry / rollback metrics | `o/metrics/MetricsService.java`, `o/metrics/MetricsResponse.java` | `GET /api/metrics` | `MetricsIntegrationTest` | ORCHESTRATION |
| 20 | MTTR | `MetricsService` — mean of (first successful attempt − first failed attempt) per recovered task, from `task_attempts` | `GET /api/metrics` | `MetricsIntegrationTest` | ORCHESTRATION |
| 21 | End-to-end latency | `MetricsService` — mean/max of `completedAt − startedAt` over completed runs | `GET /api/metrics` | `MetricsIntegrationTest` | ORCHESTRATION |
| 22 | Dynamic replanning + version history | `o/governance/ReplanService.java`, `o/domain/WorkflowGraphVersion.java`, `o/domain/WorkflowReplan.java` | `POST /api/workflows/{id}/replan`, `GET .../graph/versions`, `GET .../replans` | **`ReplanIntegrationTest`** — v2 created, every v1 row untouched, both queryable | ORCHESTRATION, SCENARIOS |
| 23 | **Greenfield scenario** | `u/url/domain/ClickEvent.java`, `u/url/repository/ClickEventRepository.java`, `u/url/service/ClickAnalyticsService.java`, `V3__click_events.sql` | `GET /api/urls/{shortCode}/analytics` (8081) | **`ClickAnalyticsIntegrationTest`** (10), `ShortUrlServiceTest` | SCENARIOS, `scenarios/greenfield-click-analytics.json` |
| 24 | **Brownfield scenario** | `u/url/service/ShortUrlService.create`, `u/url/service/ShortUrlWriter.java`, `u/url/service/ShortCodeCollisions.java`, `u/url/exception/ShortCodeCollisionException.java` | `POST /api/urls` → `201` or `409` (8081) | **`ShortUrlServiceCollisionRetryTest`** (5), **`ShortCodeCollisionRetryIntegrationTest`** (4), `ShortCodeCollisionsTest` (5) | SCENARIOS, `scenarios/brownfield-collision-retry.json` |
| 25 | **Ambiguous scenario** | `o/governance/AmbiguityDetector.java`, `o/governance/ClarificationGateService.java`, `o/agent/deterministic/KnownCapabilities.java` | `POST /api/scenarios/ambiguous-security/start`, clarification + replan endpoints | **`AmbiguousRequirementScenarioIntegrationTest`** (8), `AmbiguityDetectorTest` (14), `KnownCapabilitiesTest` (7) | SCENARIOS, `scenarios/ambiguous-security.json` |
| 26 | Scenario reproducibility | `o/scenario/ScenarioCatalog.java`, `o/scenario/Scenario.java`, `o/api/ScenarioController.java`, `scenarios/*.json` | `GET /api/scenarios`, `GET /{key}`, `POST /{key}/start` | `ScenarioCatalogIntegrationTest` (5) | SCENARIOS |
| 27 | Provider-neutral LLM extension point | `o/agent/llm/LlmClient.java`, `o/agent/llm/DeterministicLlmClient.java` | — | `DeterministicAgentTest`, `AgentRegistryTest` | ARCHITECTURE, AI_USAGE, RISKS |
| 28 | Testing strategy | `AbstractPostgresIntegrationTest` (both modules), `TestDatabase`, `ScriptedAgent`, `ScriptedFallback` | — | 180 tests, 0 failures, 0 errors, 0 skipped | TESTING |

## Reading a scenario end to end

Requirement → orchestration → decision → change → tests → audit → result:

| Step | Where to look |
| --- | --- |
| Requirement | `GET /api/scenarios/{key}` or `scenarios/{key}.json` |
| Orchestration stages | `GET /api/workflows/{id}/graph` and `/tasks` |
| Decisions | `GET /api/workflows/{id}/decisions` |
| Approval / clarification | `GET /api/workflows/{id}/approvals`, `/clarifications`, `/replans` |
| Implemented change | the classes listed in rows 23–25 |
| Tests | the tests listed in rows 23–25 |
| Audit evidence | `GET /api/workflows/{id}/audit` |
| Final result | `GET /api/workflows/{id}` and `GET /api/metrics` |
