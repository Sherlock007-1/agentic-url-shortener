# AI usage

## Tooling

**GitHub Copilot Pro+**, used inside Spring Tools Suite (Eclipse), was the development assistant for
this repository.

## How it was used

- **Human-directed architecture.** The two-service split, the persisted-DAG model, the agent
  abstraction, the governance gates and the scenario design were decided by me. Copilot was
  instructed against those decisions; it did not choose them.
- **Prompt-driven implementation in increments.** Work proceeded as reviewed increments — URL
  shortener baseline, orchestration core, governance and recovery, then the assessment scenarios and
  documentation. Each increment was specified before it was generated.
- **Everything reviewed.** Generated code was read, edited and often rejected. Two examples worth
  naming, because they are exactly the kind of thing an unreviewed suggestion gets wrong:
  - the first collision-retry implementation retried **inside a single transaction**, which cannot
    work against PostgreSQL once a constraint violation has aborted it. That is why
    `ShortUrlWriter` exists and why `ShortCodeCollisionRetryIntegrationTest` runs against a real
    database rather than a mock;
  - a configuration-properties record was given a second convenience constructor, which silently
    broke Spring Boot constructor binding until `@ConstructorBinding` was made explicit.
- **Tests as the acceptance criterion.** No increment was considered done until
  `mvnw clean verify` was green with 0 failures, 0 errors and 0 skipped, including the
  Testcontainers integration tests.
- **Version control.** Work was done on feature branches (`feat/...`), so every change is reviewable
  as a diff rather than as an assertion.

## What Copilot is *not*

- **Copilot is not part of the running system.** It is not a dependency, not a bean, not an endpoint.
  Neither service imports or calls it.
- **Copilot is not the runtime agent system.** The agents in `orchestrator-service` are ordinary Java
  classes behind the `Agent` interface. At runtime they call `LlmClient`, whose bundled
  implementation, `DeterministicLlmClient`, performs **no network I/O** and needs **no credentials**.
- **No agent edits the repository at runtime.** The implementation agent describes a change; it does
  not write source files. Scenario code changes are the change produced and validated during
  development, and the tests and migrations are the evidence.

## Why deterministic agents at runtime

Reproducibility. A reviewer can clone the repository, run `docker compose up -d` and
`mvnw clean verify`, and get the same result every time — with no API key, no account and no cost.
Tests then assert **orchestration behaviour** (ordering, parallelism, gating, replanning, lineage)
rather than model prose, which is what the prototype is actually about.

`DeterministicLlmClient` is a demo/test implementation. It is not a simulation of a model and is
never presented as one.

## Extension point

```java
public interface LlmClient {
    String complete(String role, String instruction, Map<String, String> context);
}
```

`LlmClient` is provider-neutral. Introducing OpenAI, Anthropic, Bedrock or a self-hosted model means
adding one bean. The orchestration engine, the persisted graph, the governance gates and the agents'
contracts do not change. **No external LLM credentials are required to build, test or run anything in
this repository.**

## Scope of this document

This is a curated summary, deliberately short. It does not reproduce Copilot chat transcripts, and it
does not reproduce the wording of the internal assessment brief.
