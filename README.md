# agentic-url-shortener

An agentic software-engineering prototype: an **SDLC orchestration service** that drives a **URL
shortener service** through requirement understanding, decomposition, implementation, validation and
documentation — under explicit human oversight.

The point of the prototype is **controlled autonomy**: the orchestrator does a lot on its own, but
every step is persisted, explainable and stoppable, and it asks instead of guessing.

## Architecture (two services, one database)

| Module | Port | Purpose |
| --- | --- | --- |
| `orchestrator-service` | 8080 | Agentic SDLC orchestration: persisted DAG, agents, governance, recovery, metrics |
| `url-shortener-service` | 8081 | The product under development: create, redirect, expire, disable, click analytics |

Both are independently runnable Spring Boot applications in one Maven multi-module repository,
backed by a single PostgreSQL instance using separate schemas (`orchestrator`, `shortener`).

Detail: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · [docs/ORCHESTRATION.md](docs/ORCHESTRATION.md)

## Technologies

Java 21 · Spring Boot 3.3.x (Web, Data JPA, Validation, Actuator) · PostgreSQL 16 · Flyway ·
springdoc-openapi (Swagger UI) · JUnit 5 · Testcontainers · Maven

## Prerequisites

- **JDK 21** (`java -version` must report 21)
- **Docker Desktop**, running — required both for `docker compose` and for the Testcontainers
  integration tests
- No LLM API key, no cloud account and no external service is required

## Setup

1. **Start PostgreSQL**

   ```
   docker compose up -d
   ```

2. **Build and test everything from the repository root**

   ```
   mvnw.cmd clean verify        # Windows
   ./mvnw clean verify          # Linux / macOS
   ```

   The integration tests start real PostgreSQL containers, so Docker must be running.

3. **Run the services** (two terminals, or from your IDE)

   ```
   mvnw.cmd -pl orchestrator-service spring-boot:run
   mvnw.cmd -pl url-shortener-service spring-boot:run
   ```

4. **Open Swagger** — this is the operator console for the demo

   - Orchestrator: <http://localhost:8080/swagger-ui.html> · health: <http://localhost:8080/actuator/health>
   - URL shortener: <http://localhost:8081/swagger-ui.html> · health: <http://localhost:8081/actuator/health>

Database settings default to local development values and can be overridden with environment
variables (`ORCHESTRATOR_DB_URL`, `ORCHESTRATOR_DB_USERNAME`, `ORCHESTRATOR_DB_PASSWORD`,
`SHORTENER_DB_URL`, `SHORTENER_DB_USERNAME`, `SHORTENER_DB_PASSWORD`). No credentials are committed.

## URL shortener API

| Method | Path | Behaviour |
| --- | --- | --- |
| `POST` | `/api/urls` | Create a short URL (`400` invalid destination, `409` if three code generations all collide) |
| `GET` | `/{shortCode}` | `302` redirect for an active link, `404` unknown, `410` expired/disabled. A successful redirect records one click |
| `GET` | `/api/urls/{shortCode}` | Metadata lookup (`404` unknown) |
| `GET` | `/api/urls/{shortCode}/analytics` | Click analytics: `totalClicks`, `lastClickedAt`, recent clicks (`404` unknown) |
| `DELETE` | `/api/urls/{shortCode}` | Soft disable (`204`, `404` unknown); the row is kept so the code is never reused |

Destination URLs must be absolute `http`/`https` URIs with a host and at most 2048 characters;
schemes such as `javascript:`, `file:` and `data:` are rejected. This is input validation, not SSRF
or open-redirect protection.

Analytics store only the click time and the optional `Referer` — **no IP address, user agent, device
fingerprint or geo-location**.

## Orchestrator API

| Method | Path | Behaviour |
| --- | --- | --- |
| `POST` | `/api/requirements` | Creates requirement + workflow run + persisted graph version 1 (`READY`) |
| `POST` | `/api/workflows/{id}/start` | Starts execution (idempotent while `RUNNING`) |
| `GET` | `/api/workflows/{id}` | Workflow summary, status and safe-stop reason |
| `GET` | `/api/workflows/{id}/graph` | Graph of the current version (`?version=N` for an earlier one) |
| `GET` | `/api/workflows/{id}/graph/versions` | Graph version history (replanning adds, never overwrites) |
| `GET` | `/api/workflows/{id}/tasks` | Task details including persisted cross-stage context |
| `GET` | `/api/workflows/{id}/decisions` | Decision lineage |
| `GET` | `/api/workflows/{id}/audit` | Ordered audit history |
| `GET` | `/api/workflows/{id}/approvals` | Approval gates and their status |
| `POST` | `/api/workflows/{id}/approvals/{approvalId}/approve` \| `/reject` | Human decision; reject ⇒ `SAFE_STOPPED` |
| `GET` | `/api/workflows/{id}/clarifications` | Questions the orchestrator asked instead of guessing |
| `POST` | `/api/workflows/{id}/clarifications/{clarificationId}/answer` | Answer (optionally `replan: true`) |
| `POST` | `/api/workflows/{id}/replan` · `GET` `/replans` | Create the next graph version · replan lineage |
| `GET` | `/api/workflows/{id}/snapshots` · `POST` `.../rollback` | Workspace snapshots and restore |
| `GET` | `/api/metrics` | Metrics derived from persisted records |
| `GET` | `/api/scenarios` · `/{key}` · `POST` `/{key}/start` | The three assessment scenarios, reproducibly |

The SDLC graph is **persisted data, not hard-coded control flow**:

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

## Demo sequence

A reviewer can follow this end to end; no frontend is needed.

1. `docker compose up -d`
2. Run `orchestrator-service` (8080) and `url-shortener-service` (8081)
3. Open <http://localhost:8080/swagger-ui.html>
4. `GET /api/scenarios` — see the three scenarios and where their evidence lives
5. `POST /api/scenarios/greenfield-click-analytics/start` — note the returned `workflowId`
6. `GET /api/workflows/{id}/graph`, `/tasks`, `/audit` — the persisted DAG and its history
7. `GET /api/workflows/{id}` — status is `WAITING_FOR_APPROVAL`: the **pre-implementation gate**
   blocked the implementation task. Nothing proceeds without a human.
8. `GET /api/workflows/{id}/approvals` then `POST .../approvals/{approvalId}/approve`
9. `GET /api/workflows/{id}/tasks` — `tests`, `security` and `documentation` ran **in parallel**
   (same predecessor, overlapping start times), joined by `validation`
10. `GET /api/workflows/{id}` — blocked again at the **final gate**
11. Approve the final gate
12. `GET /api/workflows/{id}` (`COMPLETED`), `/decisions`, `/audit`, and `GET /api/metrics`
13. **Ambiguous scenario**: `POST /api/scenarios/ambiguous-security/start` →
    status becomes `AWAITING_CLARIFICATION` → `GET /api/workflows/{id}/clarifications` →
    `POST .../clarifications/{clarificationId}/answer` with
    `{"answer":"Reject unsafe URL schemes and require HTTP or HTTPS URLs with a valid host.","answeredBy":"you","replan":true}`
    → `GET /api/workflows/{id}/graph/versions` shows versions **1 and 2**, version 1 still queryable →
    `POST /api/workflows/{id}/start` runs version 2 → `/decisions` records that the behaviour already
    exists and that **no duplicate implementation is required**
14. **Greenfield analytics** on <http://localhost:8081/swagger-ui.html>: `POST /api/urls`, follow
    `GET /{shortCode}` a few times, then `GET /api/urls/{shortCode}/analytics`
15. **Brownfield collision retry**: see `ShortUrlServiceCollisionRetryTest`,
    `ShortCodeCollisionRetryIntegrationTest` and `ShortCodeCollisionsTest`

Full walkthrough: [docs/SCENARIOS.md](docs/SCENARIOS.md)

## Scenarios

| Key | Type | Requirement |
| --- | --- | --- |
| `greenfield-click-analytics` | Greenfield | "Add click analytics for shortened URLs." |
| `brownfield-collision-retry` | Brownfield | "Make short-code generation collision-safe by retrying generation up to three times before failing." |
| `ambiguous-security` | Ambiguous | "Make shortened URLs more secure." |

Readable fixtures live in [`scenarios/`](scenarios); the narrative is in
[docs/SCENARIOS.md](docs/SCENARIOS.md).

## Honest scope

- The runtime agents are **deterministic**, not live LLM calls. `LlmClient` is a provider-neutral
  extension point; `DeterministicLlmClient` is the bundled implementation. **No API key is required.**
- **GitHub Copilot was the development assistant** for this repository. It is **not** part of the
  running system and **not** the runtime agent system. See [docs/AI_USAGE.md](docs/AI_USAGE.md).
- **No agent edits the repository at runtime.** Scenario code changes are the change produced and
  validated during development; the implemented code, migrations and passing tests are the evidence,
  and the workflow run is the evidence of how that change was governed.
- One orchestrator instance, no authentication, no UI beyond Swagger.

## Limitations

Single orchestrator instance (JVM lock, not distributed locking) · deterministic agents by default ·
no external LLM configured · no authentication · filesystem snapshots rather than an artefact store ·
the change-policy guard is an application-level guardrail, not a sandbox · ambiguity detection and
known-capability knowledge are curated allow-lists, not inference · analytics and metrics
intentionally minimal.

Full list and what production would need: [docs/RISKS_AND_TRADEOFFS.md](docs/RISKS_AND_TRADEOFFS.md)

## Validation

```
orchestrator-service   Tests run: 111, Failures: 0, Errors: 0, Skipped: 0
url-shortener-service  Tests run:  69, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Integration tests run against real PostgreSQL 16 via Testcontainers — no H2 and no in-memory
substitute. See [docs/TESTING.md](docs/TESTING.md).

## Documentation

| Document | Contents |
| --- | --- |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Two services, persistence, agent abstraction, scheduling, why single-instance |
| [docs/ORCHESTRATION.md](docs/ORCHESTRATION.md) | DAG, parallelism, join, context, decisions, gates, retries, rollback, replanning, audit, metrics |
| [docs/SCENARIOS.md](docs/SCENARIOS.md) | The three scenarios: requirement → workflow → decision → change → evidence |
| [docs/TESTING.md](docs/TESTING.md) | Unit vs Testcontainers tests, what each important test proves, exact build command |
| [docs/TRACEABILITY.md](docs/TRACEABILITY.md) | Capability → concrete class / endpoint / test / doc |
| [docs/RISKS_AND_TRADEOFFS.md](docs/RISKS_AND_TRADEOFFS.md) | Limitations and what production would need |
| [docs/ENGINEERING_SUMMARY.md](docs/ENGINEERING_SUMMARY.md) | Reviewer-facing summary of what was built and why |
| [docs/AI_USAGE.md](docs/AI_USAGE.md) | How GitHub Copilot was used during development |
