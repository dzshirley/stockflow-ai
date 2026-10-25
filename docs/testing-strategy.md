# Testing Strategy

## Test pyramid

- **Unit:** domain policies, calculations, validators, mappers, and state machines with JUnit/Mockito or Vitest.
- **Web/component:** Spring MVC contracts/security and React Testing Library user-visible states with network mocks.
- **Persistence/integration:** Testcontainers PostgreSQL verifies migrations, constraints, repositories, transaction rollback, idempotency, and concurrent reservations.
- **End-to-end:** Playwright exercises sign-in and key commerce/approval workflows against the integrated stack.
- **Performance:** k6 establishes endpoint-specific latency/error targets and hot-SKU contention behavior.

## Phase 1 checks

- Backend MVC test asserts status, content type, schema fields, and parseable timestamp.
- Backend smoke context uses H2 in PostgreSQL compatibility mode so CI can detect wiring/configuration errors without external services; a separate Flyway smoke test applies the foundation migration. PostgreSQL behavior still requires later Testcontainers coverage.
- Frontend service test verifies success and non-2xx error handling.
- Frontend component tests verify loading, connected, and unavailable UI states.
- Build/type/lint/test commands are combined as `pnpm check`.

## Phase 2 checks

- Unit tests cover registration normalization/ownership creation and valid/invalid order state transitions.
- MVC security tests cover anonymous rejection and ADMIN-versus-STAFF authorization for product archival, with CSRF enabled.
- The migration test applies V1 and V2 and checks all ten implemented tables.
- PostgreSQL 18 Testcontainers tests prove cross-store isolation, exactly one winner for two concurrent last-item reservations, idempotent order/payment replay, cancellation release, and rollback of an earlier reservation when a later line is unavailable.
- The Phase 2 verification originally executed 11 fast tests and 5 PostgreSQL integration tests through Maven Failsafe.
- Docker Compose verification applies Flyway V2 to the existing Phase 1 volume and checks all three service health probes.
- An authenticated runtime smoke covers CSRF bootstrap, registration, product creation, order creation, approved payment, and the resulting inventory balance.

## Phase 3 checks

- React Testing Library covers login validation/navigation/session messaging, authenticated/public route guards, product validation/normalization, and inventory-adjustment audit inputs.
- Service tests cover typed health responses, non-2xx behavior, order CSRF and idempotency headers, structured 409 conflict propagation, and idempotency-key fallback outside secure browser contexts.
- `pnpm check` passes TypeScript strict compilation, zero-warning ESLint, 17 Vitest tests, and the production Vite build.
- The backend suite now runs 11 fast tests plus 6 PostgreSQL tests (17 total); the added regression proves absent product search filters work with PostgreSQL parameter typing.
- Playwright runs Chromium against the production Compose stack and covers protected-route redirect, registration, product creation, immediate inventory visibility, audited adjustment, idempotent order creation, payment approval, and logout.
- Manual browser verification additionally checks the final inventory balance, zero console errors, and a 390-pixel mobile viewport with no horizontal overflow.

## Phase 4 checks

- Calculation unit tests cover AOV, revenue growth, zero denominators, sales velocity, and days of cover.
- Period tests use a fixed clock to prove store-time-zone boundaries, complete calendar-month comparison, invalid ranges, and the 366-day limit.
- MVC tests prove anonymous analytics rejection, authenticated access, and bounded result limits.
- Flyway smoke applies V1–V3; PostgreSQL Testcontainers verifies paid/cancelled inclusion, multi-line duplicate-count prevention, product/category aggregation, inventory totals, tenant isolation, empty comparison behavior, and inclusive-start/exclusive-end payment boundaries.
- React tests cover query serialization, KPI/comparison rendering, custom date selection, API-backed rankings/alerts, loading, empty, and error states. Recharts itself is replaced by a deterministic component stub in page unit tests; rendered chart behavior is verified against the running stack.
- Commerce mutations invalidate the shared `analytics` query family. The production-stack E2E test proves a newly paid two-unit order immediately appears as `$59.98`, one paid order, two units, and the expected top product; this caught and prevented stale dashboard-cache reuse.
- `scripts/performance/analytics-smoke.js` creates an isolated tenant with 100 paid orders and measures five authenticated analytics reads. It preserves each virtual user's session cookie between iterations, matching the SPA's session model. Thresholds are under 1% request failures, overall p95 under 1 second, and analytics-tagged p95 under 750 ms for the local profile.
- `scripts/seed/analytics-demo.sql` creates six products across three categories and 120 deterministic paid/cancelled orders across four months without touching non-`DEMO-*` records; the paired reset script removes only that scoped fixture.

### Phase 4 verification record (2026-10-21)

- Backend Java 21 verification: 19 unit/web/migration tests and 8 PostgreSQL 18 Testcontainers integration tests passed (27 total).
- Frontend Node 24 verification: strict TypeScript, zero-warning ESLint, 24 Vitest tests across 11 files, and the production Vite build passed.
- Production Compose stack: PostgreSQL, backend, and frontend health checks passed; Flyway validated migrations V1–V3 and Hibernate validated the schema.
- Playwright Chromium: the register → product → inventory adjustment → order → approved payment → analytics → logout workflow passed in 3.0 seconds.
- Manual rendered QA: real line/bar marks, period-filter refresh, no browser console warnings/errors, and no horizontal overflow at a 390-pixel viewport.
- Seed/reset SQL: inserted 6 scoped products and 120 orders, then removed exactly 120 orders and 6 products from the isolated tenant.
- k6 local profile: 100 paid orders, 5 virtual users, 10 seconds, 2,779 completed dashboard iterations, 0.00% failed requests, 3.40 ms mean analytics latency, 2.35 ms analytics p50, and 7.65 ms analytics p95. These are local development measurements, not production capacity claims.
- Known advisory: the production JavaScript chunk is 733.13 kB minified (214.78 kB gzip), above Vite's 500 kB warning threshold because Recharts is currently in the main bundle. Route-level code splitting is deferred until it is measured as a user-facing startup problem.

## Phase 5 checks

- A 56-row checked-in prompt fixture validates deterministic mock tool selection, presets, interval, result bounds, multi-tool routing, and unsupported requests. A separate case proves conversation follow-up intent.
- Response-assembler tests prove that typed tool results, not a provider's fabricated numeric prose, supply authoritative metrics and that multi-tool data produces stable response sections.
- Tool-adapter tests prove server-injected tenant context and reject out-of-range limits before analytics execution.
- MVC tests cover anonymous denial, CSRF, payload validation, and authenticated conversation listing.
- PostgreSQL integration extends the complete commerce workflow through a `$59.98` paid order, revenue question, visualization follow-up, four persisted messages, two successful audit rows, and cross-tenant conversation denial.
- Frontend tests cover query/list/detail serialization and user-visible conversation history, loading, retryable errors, metric rendering, and chart rendering.
- Playwright extends the production-stack registration → product → stock → paid order → analytics flow with a revenue question, exact verified metric, and contextual chart follow-up.

### Phase 5 verification record (2026-10-22)

- Backend Java 21 verification: 84 unit/web/migration/evaluation tests and 9 PostgreSQL 18 Testcontainers integration tests passed (93 total).
- Frontend Node 24 verification: strict TypeScript, zero-warning ESLint, 31 Vitest tests across 13 files, and the production Vite build passed.
- Production Compose stack: fresh backend/frontend images built, PostgreSQL/backend/frontend health checks passed, Flyway validated V1–V4 and applied V4 to the existing volume, and direct plus frontend-proxied health endpoints returned `UP`.
- Playwright Chromium: the full registration → product → inventory → paid order → analytics → AI revenue `$59.98` → contextual daily chart → logout workflow passed in 2.8 seconds (3.4 seconds total runner time).
- Production build advisory: the JavaScript chunk is 753.66 kB minified (220 kB gzip), still above Vite's warning threshold; route splitting remains a measured optimization, not a correctness blocker.
- Default tests use `AI_PROVIDER=mock`; no paid API, external network, or probabilistic model score is required. See [AI evaluation](ai-evaluation.md) for scope and gaps.

## Phase 5.5 checks

- MockWebServer exercises delayed-response timeout, connection reset, 429, 500/502/503, malformed JSON, and interrupted bodies against the real OpenAI adapter. Assertions bound total attempts and elapsed time.
- Cost/telemetry unit tests verify provider usage, exact decimal estimation, unpriced models, failed-request null usage, privacy-minimized fields, and Micrometer recording.
- Flyway smoke applies V1–V5 and validates all 14 implemented tables; PostgreSQL workflow verifies owner-attributed telemetry beside deterministic tool evidence.
- Outage isolation uses a failed provider bean and proves product retrieval, inventory quantity, and order count remain correct.
- Evidence-contract tests reject dangling references, unverifiable calculations, invalid periods/IDs, and recommendations without approval.
- `LiveOpenAiEvaluationIT` is compiled but class-level skipped unless `RUN_LIVE_AI_EVAL=true`; it is never part of ordinary CI billing.

### Phase 5.5 verification record (2026-10-22)

- Backend Java 21 verification: 102 fast/unit/MVC/migration/evaluation/fault tests and 10 non-billable integration tests passed (112 passed); 1 live-provider integration test was skipped by default.
- Frontend Node 24 verification: strict TypeScript, zero-warning ESLint, 31 Vitest tests across 13 files, and production build passed.
- Production backend image built; the local PostgreSQL/backend stack reported healthy, Flyway V5 applied successfully, and `/api/health` returned `UP`.
- No paid live-provider evaluation was approved or executed, so real model selection/argument behavior remains explicitly unverified.

## Phase 6 checks

- Calculation tests cover positive/negative/zero changes, zero denominators, contribution reconciliation, new/discontinued products, symmetric price-volume effects, scenario profit/coverage arithmetic, unavailable cost evidence, and percentage bounds.
- A 44-row deterministic question fixture verifies revenue, inventory, scenario, unsupported prediction/mutation/SQL, causal-language, and cross-store injection routing without a paid model.
- Schema tests reject dangling evidence, missing calculation definitions, invalid ownership/periods, and recommendations without approval. Malformed or invented provider selection codes fail closed.
- MVC tests cover anonymous creation/retrieval, CSRF/idempotency requirements, and authenticated access. PostgreSQL integration covers JSONB/version preservation, lifecycle, idempotent replay/conflict, deterministic values, ADMIN/STAFF/cross-store policy, inventory/decomposition evidence, and scenarios.
- A provider-failure integration test verifies an explicit `FAILED` row with no report document. The existing AI outage tests continue to prove commerce independence.
- React tests cover typed report services, creation/history/detail rendering, epistemic-layer labels, scenarios, evidence, validation, and failed-report state. Playwright extends the full commerce flow through report generation and persisted verified detail.

### Phase 6 verification record (2026-10-22)

- Backend Java 21 verification: 159 fast/unit/MVC/migration/evaluation/fault tests and 14 non-billable PostgreSQL integration tests passed (173 total); 1 live-provider test was skipped by default.
- Business-analysis evaluation: 44/44 deterministic routing cases passed, with numeric/evidence/schema behavior covered by the companion calculation and integration suites.
- Frontend Node 24 verification: strict TypeScript, zero-warning ESLint, 37 Vitest tests across 15 files, and production build passed.
- Production Compose: fresh backend/frontend images built; PostgreSQL, backend, and frontend health checks passed; Flyway V6 was successful; direct and proxied health returned `UP`.
- Playwright Chromium: the complete protected registration → commerce → paid analytics → AI follow-up → persisted Business Insights report → logout workflow passed in 4.3 seconds (6.4 seconds total runner time).
- Production build advisory: the JavaScript chunk is 767.27 kB minified (222.96 kB gzip), above Vite's 500 kB warning threshold. Route-level splitting remains a performance improvement rather than a correctness blocker.
- No paid live-provider evaluation was authorized or executed; real-provider Phase 6 structured synthesis remains unverified.

## Critical later suites

- Full HTTP contract tests for each endpoint and field-level error payload.
- Additional payment/expiration races and scheduled-expiration clock fixtures;
  payment/cancellation concurrency is covered in Phase 8.
- Duplicate message/approval execution behavior.
- Historical inventory snapshots/stockout duration once that data model exists, plus sustained large-store query plans before materialization or caching.
- Approved live-provider execution and longitudinal model-quality/cost baselines.
- Missing-data phrasing variants, adversarial retrieved-text suites, and custom natural-language date parsing beyond the current allow-listed presets.
- Repeated approval behavior remains Phase 7 scope; Phase 5 tools are read-only and cannot create or execute actions.

Probabilistic model evaluation is never used as a substitute for deterministic tool and domain tests.

## Phase 7 checks

- PostgreSQL workflow coverage verifies proposal generation/replay, exact-version
  transitions, ADMIN/STAFF and cross-store boundaries, approval without stock
  mutation, explicit receipt confirmation, stale inventory, duplicate command
  replay, concurrent commands, audit/outbox uniqueness, event order, and expired
  execution-lease recovery.
- Provider tests reject malformed output and product IDs outside the bounded
  candidate set, and propagate provider outage without exposing a mutation tool.
- Frontend service tests verify CSRF/idempotency headers. Operations UI tests
  verify receipt confirmation gates execution and render evidence/assumptions.
- Live-provider restock behavior was not run and remains unverified. The
  deterministic mock is the required CI baseline.

### Phase 7 verification record (2026-10-23)

- Full Java 21 backend verification passed 162 unit/MVC/migration/evaluation/fault
  tests and 23 non-billable integration tests; the one live-provider evaluation
  remained skipped (185 passed, 1 skipped). Two final acceptance tests for
  provider usage and outbox rollback/retry then passed in a focused run,
  bringing the checked-in non-billable suite to 187 tests. After the browser-discovered
  PostgreSQL timestamp precision correction, the 12 Phase 6 integrity and Phase
  7 operations workflow tests passed again.
- An injected PostgreSQL outbox-trigger failure proves the inventory adjustment,
  proposal completion, and outbox write roll back together; the separately
  recorded `FAILED` attempt can then be retried explicitly and succeeds once.
- Frontend strict TypeScript, zero-warning ESLint, 41 Vitest tests across 17
  files, and the Vite production build passed. A subsequent reliability review
  split routes lazily, reducing the initial production chunk from 777.47 kB
  minified/225.15 kB gzip to 257.19 kB/79.55 kB; the 500-kB advisory is gone.
- Docker Compose rebuilt healthy backend/frontend images, Flyway advanced the
  persistent PostgreSQL database from V6 through V9, and all three services
  reported healthy.
- Two Playwright Chromium workflows passed: the existing protected commerce →
  analytics → AI → Business Insights flow and a new completed report → proposal
  → ADMIN approval → explicit receipt → inventory audit flow.

### Phase 7 reliability review (2026-10-23)

- A prior full run recorded the stale-inventory test at about 897 seconds but
  retained no blocker or thread evidence. Three controlled class runs passed in
  about 16 seconds each; the affected case took about 0.45 seconds. A database
  deadlock was not reproduced or established.
- The workflow integration class now has 30-second JUnit/startup/statement
  bounds, a five-second PostgreSQL lock timeout, ten-second concurrent-future
  bounds, and delayed database blocker plus JVM thread diagnostics.
- The default full verification still skips the billable live-provider class.
  Its argument assertions and hard USD 0.05 cost ceiling are verified by
  compilation/discovery, not by a real provider call.
- The post-change Java 21 full gate passed 163 fast tests and 24 non-billable
  integration tests (187 passed); the one live-provider test was skipped. The
  full run completed in 1 minute 24 seconds, and the stale-inventory case took
  0.402 seconds. Frontend typecheck, zero-warning lint, 41 tests, and production
  build passed, followed by both Playwright Chromium workflows against rebuilt
  Compose images.
- Full details and remaining hypotheses are in the
  [Phase 7 reliability review](phase7-reliability-review.md).

## Phase 8 hardening coverage

- Login guard unit tests cover threshold enforcement, success reset, expiry,
  and remote-address isolation without storing raw identifiers.
- Request observability tests cover accepted/rejected correlation IDs, response
  propagation, route/status timers, and MDC cleanup.
- Operations MVC tests cover anonymous denial, CSRF and idempotency headers,
  bounded list input, and receipt confirmation validation.
- PostgreSQL workflow tests now cover concurrent same-key order creation,
  payment-versus-cancellation terminal races, repeated stale-proposal
  validation, and idempotent recovery. Integration tests use bounded JUnit,
  future, startup, lock, and statement waits rather than unlimited blocking.
- Playwright adds one negative real-stack workflow for order replay/conflict,
  insufficient inventory, invalid terminal transition, and cross-store
  isolation. The run also discovered that `crypto.randomUUID` is unavailable on
  a non-secure `host.docker.internal` origin; the Web Crypto fallback is covered
  by Vitest and the corrected workflows pass in that environment.
- `scripts/performance/phase8-api-smoke.js` creates only an isolated synthetic
  tenant and asserts every endpoint result while recording endpoint-tagged
  latency and error rates. It is a smoke benchmark, not a correctness suite or
  production capacity test.
- CI runs deterministic mock-provider tests only. The live provider class is
  discovered and skipped unless explicitly enabled; no Phase 8 command enables
  it.

The final verified counts and runner outcomes are recorded in
[Phase 8 verification](phase8-verification.md) and
[engineering metrics](engineering-metrics.md).

## Phase 9 Stage A verification

Deployment preparation adds one MVC regression proving that the production
registration switch rejects `POST /api/auth/register` before the service runs.
The complete existing Java 21 gate remains authoritative for commerce,
PostgreSQL migrations, concurrency, tenant isolation, evidence, proposal
execution, and provider fault isolation. Frontend `pnpm check` continues to run
strict TypeScript, zero-warning ESLint, Vitest, the production build, and both
initial/chunk bundle budgets.

Infrastructure checks are deliberately non-provisioning: `terraform fmt
-check`, `terraform init -backend=false`, and `terraform validate`; YAML parsing
for every workflow; POSIX shell syntax for demo tools; Docker image builds; and
`docker compose config`. These establish syntax/provider-schema/build evidence,
not an AWS plan or a successful deployment. An authenticated AWS plan is Stage
B because it resolves account state and can reveal billable resources.

The final local gate rebuilds Compose, waits for PostgreSQL/backend/frontend
health, verifies direct/proxied health and production security headers, runs the
three Playwright Chromium workflows, executes the reproducible synthetic seed
and mock-AI preparation, and captures screenshots through the working browser.
Live OpenAI evaluation remains skipped and no billable request is authorized.

Exact Stage A commands, counts, pass/skip outcomes, and unverified cloud behavior
are recorded in [phase9-verification.md](phase9-verification.md). Remote GitHub
Actions and AWS deployment are not claimed until they execute on a pushed,
reviewed commit after explicit approval.
