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
| `GET` | `/api/workflows/{id}` | Workflow summary and status |
| `GET` | `/api/workflows/{id}/graph` | Graph version, tasks, statuses and dependency edges |
| `GET` | `/api/workflows/{id}/tasks` | Task details including persisted input/output context |
| `GET` | `/api/workflows/{id}/decisions` | Recorded planning/architecture decisions |
| `GET` | `/api/workflows/{id}/audit` | Ordered audit history |

The SDLC graph is persisted (not hard-coded control flow):
`requirement-analysis → codebase-analysis → planning → architecture → implementation`,
then `tests`, `security` and `documentation` in parallel, joined by `validation`.
Agents are provider-neutral (`LlmClient`); the bundled implementations are deterministic and
require no API credentials.

## Status

Implementation is intentionally incremental. Implemented so far: the URL shortener baseline
(creation, redirect, metadata, soft disable, expiration) and the orchestration core (persisted DAG,
parallel execution with join, agent abstraction, cross-stage context, decision lineage, audit trail).
Approvals, retry/rollback, safe-stop, dynamic replanning, metrics, click analytics and collision-safe
short-code generation are added in subsequent increments.

