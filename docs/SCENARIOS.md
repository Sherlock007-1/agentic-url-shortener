# Scenarios

Three scenarios exercise the prototype: a greenfield feature, a brownfield change, and an ambiguous
requirement.

Machine-readable fixtures: [`scenarios/greenfield-click-analytics.json`](../scenarios/greenfield-click-analytics.json),
[`scenarios/brownfield-collision-retry.json`](../scenarios/brownfield-collision-retry.json),
[`scenarios/ambiguous-security.json`](../scenarios/ambiguous-security.json).
They are also exposed at `GET /api/scenarios`.

## A note on what the demo proves

The scenario code changes in `url-shortener-service` are the change **produced and validated during
development** of this repository, with GitHub Copilot as the development assistant.

When you run the demo, the deterministic runtime agents demonstrate the **orchestration and
governance behaviour** — decomposition, sequencing, parallelism, gates, clarification, replanning,
lineage, audit. They do **not** edit the repository live, and nothing here claims they do. The
implemented code, the Flyway migrations and the passing tests are the evidence of the change; the
workflow run is the evidence of how that change was governed.

This separation is intentional: a prototype that actually rewrote its own source at demo time would
be less safe and less reviewable, not more impressive.

---

## 1. Greenfield — click analytics

**Requirement:** `Add click analytics for shortened URLs.`

**Why greenfield.** The baseline had no analytics of any kind: no table, no entity, no endpoint,
no counter. `V2__short_urls.sql` even says analytics tables are intentionally not created. The
increment adds a capability rather than changing behaviour.

**Workflow path.** Unambiguous, so the clarification gate stays silent. The full DAG runs:
sequential analysis/planning/architecture → `PRE_IMPLEMENTATION` approval → parallel
tests/security/documentation → `validation` join → `FINAL` approval → `COMPLETED`.

**Actual change.**

| Component | Change |
| --- | --- |
| `V3__click_events.sql` | **New** migration. `click_events(id, short_url_id, clicked_at, referrer)` + index `(short_url_id, clicked_at DESC)`. Existing migrations untouched. |
| `ClickEvent` | New entity; truncates an over-long referrer instead of failing a redirect |
| `ClickEventRepository` | `countByShortUrlId`, `findByShortUrlIdOrderByClickedAtDesc(id, pageable)` |
| `ClickAnalyticsService` | Records one click; reads total, last click and a 10-entry recent window |
| `ShortUrlService.resolveDestination` | Records a click **only after** the link is known active and unexpired |
| `RedirectController` | Optional `Referer` header passed through |
| `ShortUrlController` | `GET /api/urls/{shortCode}/analytics` |

**Key decision — privacy by design.** Only the click time and the optional `Referer` are stored. No
IP address, user agent, device fingerprint or geo-location: none of it is needed for click counts,
and not collecting it removes an entire class of personal-data handling. This is enforced by the
schema, not by convention.

**Key decision — ordering.** The click is recorded *after* validity is established, so a 404 or a
410 can never inflate analytics.

**Evidence.**

- `ClickAnalyticsIntegrationTest` (PostgreSQL, 10 tests): one redirect = one click; three redirects
  = `totalClicks: 3`; unknown/expired/disabled record nothing; a never-clicked URL returns
  `totalClicks: 0` rather than a 404; counts survive a repository reload; the analytics payload has
  exactly four fields and none of them is personal data.
- `ShortUrlServiceTest`: click recorded on success, `verifyNoInteractions` on 404/410 paths.
- `ShortUrlApiIntegrationTest`: all baseline behaviour still passes; the analytics path is published
  in the OpenAPI document.

**Demo.** On <http://localhost:8081/swagger-ui.html>: `POST /api/urls`, then `GET /{shortCode}` a
few times, then `GET /api/urls/{shortCode}/analytics`. Disable it with `DELETE /api/urls/{shortCode}`
and confirm a further redirect returns 410 and does not increase the count.

---

## 2. Brownfield — collision-safe short-code generation

**Requirement:**
`Make short-code generation collision-safe by retrying generation up to three times before failing.`

**Why brownfield.** The create path already existed and deliberately did *not* retry: it generated
one code, saved it, and let the `uk_short_urls_short_code` unique constraint surface a conflict. The
increment modifies that existing behaviour in place — same service, same generator, same constraint.

**Workflow path.** Unambiguous. Same full DAG as the greenfield scenario, with `codebase-analysis`
identifying the existing create path as the change site and `PRE_IMPLEMENTATION` approval required
before existing behaviour is modified.

**Actual change.**

| Component | Change |
| --- | --- |
| `ShortUrlService.create` | Bounded loop: generate → insert → on a *short-code duplicate* only, regenerate. Max 3 attempts, then `ShortCodeCollisionException`. |
| `ShortUrlWriter` (**new**) | One `REQUIRES_NEW` transaction per attempt |
| `ShortCodeCollisions` (**new**) | Narrow classifier: is this integrity violation a duplicate *short code*? |
| `ShortCodeCollisionException` (**new**) | Carries the attempt count |
| `ShortUrlExceptionHandler` | Maps it to `409 Conflict`; other integrity violations keep their own handler |
| `ShortenerProperties` | `maxCodeGenerationAttempts` (default 3), configured in `application.yml` |
| `ShortCodeGenerator` | Javadoc updated; `generate()` stays overridable so tests can be deterministic |

**Key decision — one transaction per attempt.** This is the non-obvious part. A PostgreSQL
constraint violation aborts the surrounding transaction: once it has fired, no further statement may
run in it. Retrying inside the same transaction would fail with *"current transaction is aborted"*
instead of inserting the regenerated code. `ShortUrlWriter` gives each attempt its own
`REQUIRES_NEW` transaction, so a collision rolls back exactly one failed insert and the next attempt
starts with a clean transaction and a clean persistence context. A retry loop that is not
transaction-aware would look correct in unit tests and break against a real database — which is why
`ShortCodeCollisionRetryIntegrationTest` runs against real PostgreSQL.

**Key decision — retry only the right failure.** `ShortCodeCollisions` matches only the named
constraint or an explicit duplicate on `short_code`. A not-null violation, a different unique
constraint or a corrupted payload is rethrown on the first attempt: retrying those would mask a
defect. The database constraint remains the final safety boundary; the retry only avoids failing a
request that a fresh code would satisfy.

**Key decision — determinism in tests.** Collisions are produced by stubbing the generator with a
fixed sequence of codes, never by waiting for a real random collision.

**Evidence.**

- `ShortUrlServiceCollisionRetryTest` (5): 1 collision → success on the 2nd code; 2 collisions →
  success on the 3rd; 3 collisions → `ShortCodeCollisionException(attempts=3)` with the 4th queued
  code **never requested**; an unrelated integrity violation is not retried; first-attempt success is
  unchanged from the baseline.
- `ShortCodeCollisionsTest` (5): the classifier accepts the named constraint and a nested duplicate
  cause, and rejects a not-null violation, a different unique constraint and a null message.
- `ShortCodeCollisionRetryIntegrationTest` (4, real PostgreSQL): pre-existing rows make the real
  constraint fire; 1 and 2 collisions → `201`; 3 collisions → `409` with detail "3 attempts" and no
  extra row written.
- `ShortUrlRepositoryIntegrationTest.shortCodeIsUniqueAtTheDatabaseLevel`: the boundary still exists.

**Not to be confused with** the orchestrator's agent retry policy
(`orchestrator.governance.max-task-attempts`). Different service, different layer, different purpose.

**Demo.** This one is proven by tests rather than by clicking, because forcing a collision through
the API requires controlling the generator:
`mvnw.cmd -pl url-shortener-service test -Dtest=ShortUrlServiceCollisionRetryTest+ShortCodeCollisionRetryIntegrationTest+ShortCodeCollisionsTest`

---

## 3. Ambiguous — "make shortened URLs more secure"

**Requirement:** `Make shortened URLs more secure.`

**Why ambiguous.** It names a goal, not a change. "Secure" could mean rejecting unsafe URL schemes,
authenticating the management API, rate limiting redirects, or increasing short-code entropy. Those
are four different changes with four different risk profiles. Picking one autonomously would be a
guess presented as a decision.

**Workflow path.**

```
POST /api/scenarios/ambiguous-security/start
  -> requirement-analysis COMPLETED
  -> clarification gate (in front of codebase-analysis) recognises the ambiguity
  -> CLARIFICATION decision recorded (why it stopped)
  -> CLARIFICATION_REQUESTED audit event
  -> status AWAITING_CLARIFICATION            <-- everything downstream still PENDING
  -> human answers with replan = true
  -> CLARIFICATION_ANSWERED
  -> requirement text replaced by the answer
  -> graph version 2 created; version 1 preserved and queryable
  -> REPLAN_STARTED / GRAPH_CREATED / REPLAN_COMPLETED + REPLAN decision
  -> status READY (a human must start it; gates must be approved again)
  -> version 2 runs to COMPLETED under the clarified requirement
```

**Example human answer (used by the tests and the demo):**
`Reject unsafe URL schemes and require HTTP or HTTPS URLs with a valid host.`

**The honest outcome.** The URL shortener **already does this** — `UrlValidator` has enforced
absolute `http`/`https` URIs with a host since the baseline, and `javascript:`, `file:` and `data:`
have always been rejected with a `400`.

So under graph version 2:

- `codebase-analysis` records a `CODEBASE_ANALYSIS` decision: *"Requested capability already exists:
  url-scheme-validation"*, with the implementing classes and the tests that cover it as evidence;
- `implementation` records an `IMPLEMENTATION` decision: *"No code change required"*, explaining that
  re-implementing it would duplicate behaviour and add regression risk without adding value.

This scenario is **not** claiming that URL validation was invented now. It demonstrates ambiguity
handling, clarification, replanning, version history and controlled autonomy — and the genuinely
useful engineering behaviour at the end of that chain: *clarify the requirement → inspect the
codebase → discover the capability exists → do not write redundant code → keep the decision
traceable.* Deciding not to write code is a recorded outcome, not a skipped step.

**What was actually built for this scenario** (all in `orchestrator-service`):

| Component | Purpose |
| --- | --- |
| `AmbiguityDetector` | A short, explicit list of known-ambiguous phrasings. Not a general classifier. |
| `ClarificationGateService` | Gate in front of `codebase-analysis`; parks the run, records the rationale, asks once |
| `WorkflowTransitionService` | Checks the gate while claiming tasks, next to the approval gate |
| `KnownCapabilities` | Human-configured "this already exists" knowledge + the two decisions above |
| `CodebaseAnalysisAgent`, `ImplementationAgent` | Emit those decisions when the capability matches |
| reused unchanged | `ClarificationService`, `ReplanService`, `GovernanceWorkflowService`, `AuditService` |

**Key decision — narrow triggering.** Ambiguity detection is deliberately a small allow-list, not a
heuristic over every requirement. If everything looked ambiguous the gate would become a permanent
stop and would prove nothing. `AmbiguityDetectorTest` asserts both directions, and
`ScenarioCatalogIntegrationTest` asserts that of the three catalogued scenarios **only** the
ambiguous one trips the gate.

**Key decision — ask once.** If the question has already been answered for this run, the gate lets
the workflow through even if the stored text is unchanged. That is what makes it a stop-and-ask
rather than a loop (`theSameQuestionIsNeverAskedTwice`).

**Evidence.**

- `AmbiguousRequirementScenarioIntegrationTest` (8): not actioned autonomously and everything
  downstream still `PENDING`; question + rationale persisted and readable over the API; answering
  with a replan creates v2 while v1 stays queryable; replan lineage names both requirement versions;
  v2 completes and records both "already exists" and "no change required"; the same question is never
  asked twice; an unambiguous requirement is never parked; the scenario starts reproducibly through
  `POST /api/scenarios/{key}/start`.
- `AmbiguityDetectorTest` (14), `KnownCapabilitiesTest` (7), `ScenarioCatalogIntegrationTest` (5).
- The reused mechanisms keep their own tests: `ClarificationGateIntegrationTest`,
  `ReplanIntegrationTest`.

**Demo.**

1. `POST /api/scenarios/ambiguous-security/start`
2. `GET /api/workflows/{id}` → `AWAITING_CLARIFICATION`
3. `GET /api/workflows/{id}/clarifications` → the persisted question
4. `GET /api/workflows/{id}/decisions` → the `CLARIFICATION` decision explaining the stop
5. `POST /api/workflows/{id}/clarifications/{clarificationId}/answer`

   ```json
   {
     "answer": "Reject unsafe URL schemes and require HTTP or HTTPS URLs with a valid host.",
     "answeredBy": "product-owner",
     "replan": true
   }
   ```

6. `GET /api/workflows/{id}/graph/versions` → versions 1 and 2, version 2 current
7. `GET /api/workflows/{id}/graph?version=1` → version 1 still intact
8. `GET /api/workflows/{id}/replans` → from 1 to 2, old and new requirement, triggering clarification
9. `POST /api/workflows/{id}/start`, approve the two gates
10. `GET /api/workflows/{id}/decisions` → "already exists" and "no code change required"
11. `GET /api/workflows/{id}/audit` → the whole story in order
