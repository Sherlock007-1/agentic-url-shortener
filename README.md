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

## Status

Implementation is intentionally incremental. This commit contains only the project foundation
(module layout, configuration, baseline migrations, context-load tests). Orchestration and URL
shortener behaviour are added in subsequent commits.