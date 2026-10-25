# Implementation Plan

Checked items must have implementation evidence and a successful applicable verification, unless marked with an explicit environment limitation.

## Phase 0 — Planning and architecture

- [x] Define product, users, functional scope, and non-functional requirements.
- [x] Define modular-monolith system architecture and repository boundaries.
- [x] Design the target relational model and ER diagram.
- [x] Define the initial API contract and error conventions.
- [x] Define bounded AI orchestration and approval architecture.
- [x] Define KPI formulas, dates, currency, and inclusion rules.
- [x] Record initial engineering decisions and phased milestones.
- [x] Define Phase 1 acceptance criteria.

## Phase 1 — Project foundation

- [x] Initialize Git and monorepo structure.
- [x] Add repository guidance in `AGENTS.md`.
- [x] Create Java 21/Spring Boot backend configuration.
- [x] Configure PostgreSQL, JPA validation, and Flyway.
- [x] Add an initial identity/store migration.
- [x] Implement `GET /api/health` with a typed response.
- [x] Create React/TypeScript/Vite frontend with routing and TanStack Query.
- [x] Display backend connectivity with loading, healthy, and error states.
- [x] Add backend MVC/smoke tests and frontend component/service tests.
- [x] Add development and production-style containers plus Compose health checks.
- [x] Add CI definitions for backend and frontend checks.
- [x] Run `./mvnw verify` under a temporary Java 21 runtime.
- [x] Run `pnpm install --frozen-lockfile && pnpm check` with the bundled Node 24 runtime.
- [x] Run `docker compose up --build --wait` locally; confirm all services are healthy, Flyway V1 is applied, and direct/proxied health endpoints respond.
- [x] Document exact startup, checks, migration behavior, and troubleshooting.

### Phase 1 acceptance criteria

- Repository contains coherent source, tests, docs, environment template, and container workflows.
- With the documented prerequisites, Compose starts PostgreSQL, the API, and the frontend; health checks gate dependent services.
- `GET /api/health` returns HTTP 200 and the documented JSON shape.
- The home page visibly reflects loading, connected, and failure states from the real API request.
- Automated checks are deterministic and do not require a paid service or AI key.
- No Phase 2 business behavior is falsely represented as implemented.

## Phase 2 — Core business backend

- [x] Add secure registration, login/logout, CSRF protection, and server-derived store context.
- [x] Enforce ADMIN/STAFF permissions and cross-store isolation.
- [x] Implement product CRUD/search/filter/sort/page/archive with store-local SKU uniqueness.
- [x] Implement inventory balances, adjustments, audits, low-stock queries, and atomic reservations.
- [x] Implement idempotent order creation, mock payment, cancellation/expiration, and transition policy.
- [x] Persist order events in a transactional outbox for a later relay/consumer milestone.
- [x] Add unit, MVC, migration, Testcontainers, concurrency, isolation, idempotency, cancellation, and rollback tests.
- [x] Run `./mvnw verify` with Java 21 and PostgreSQL 18 Testcontainers: 16 tests passed.
- [x] Run `docker compose up -d --build --wait`; confirm all services are healthy and Flyway V2 is applied.
- [x] Exercise registration, CSRF, product creation, order reservation/payment, and final inventory through the running API.

### Phase 2 acceptance criteria

- Authenticated identity, store, and role come from a server-side session, never a request-supplied store ID.
- Every tenant resource query includes the authenticated `store_id`; unauthorized IDs are indistinguishable from missing IDs.
- Inventory never becomes negative and concurrent last-unit attempts produce exactly one successful reservation.
- Order creation, inventory reservation, audit rows, idempotency completion, and outbox event commit or roll back together.
- Repeating an order command with the same key and payload returns the existing order without reserving twice; a different payload is rejected.
- Payment, cancellation, and expiration are explicit terminal state transitions and release/consume inventory at most once.
- At the Phase 2 signoff, Phase 3 UI work had not started; its completed evidence is recorded below.

## Phase 3 — Authenticated commerce frontend

- [x] Add registration, login/logout, session restoration/expiry messaging, protected routes, and ADMIN/STAFF-aware controls.
- [x] Add the responsive application shell and truthful dashboard metrics backed by current APIs.
- [x] Add product search/pagination/create/edit/archive/detail workflows and client/server validation feedback.
- [x] Add inventory search/low-stock views, balance detail, auditable adjustments, and history pagination.
- [x] Add order list/detail/create/payment/cancel workflows with availability guidance and double-submit protection.
- [x] Centralize typed API calls, cookie credentials, CSRF bootstrap, Problem Details errors, and environment-based API base URL.
- [x] Add loading skeletons, empty/error states, confirmation dialogs, toasts, accessible labels, and mobile navigation.
- [x] Keep analytics and AI navigation as explicit future-phase placeholders without invented data.
- [x] Add React Testing Library and service coverage; 17 frontend tests pass.
- [x] Add and run a Playwright real-stack registration-to-paid-order workflow; 1 E2E test passes.
- [x] Run `./mvnw verify` after adding a PostgreSQL product-search regression: 17 backend tests pass.
- [x] Run `docker compose up -d --build --wait`; all three services are healthy and manual browser verification passes.
- [x] Update README, architecture, API, testing, implementation, and decision documentation.

### Phase 3 acceptance criteria

- Anonymous users cannot enter commerce routes; expired sessions return to login with clear context.
- All displayed operational data comes from store-scoped backend endpoints; no fake analytics or revenue is shown.
- Product, stock, and order mutations provide validation, pending-state protection, durable success/error feedback, and coherent cache refresh.
- The order form sends one cryptographically random idempotency key for its draft and the backend remains the final availability/concurrency authority.
- A production Compose build supports the full register → product → adjustment → order → payment → logout workflow in Chromium.

## Phase 4 — Analytics engine and business intelligence dashboard

- [x] Define paid-sales, order, AOV, units, comparison, product/category, velocity, coverage, and risk semantics.
- [x] Add immutable payment/category/cost snapshots and analytics-oriented indexes through Flyway V3.
- [x] Implement store-scoped PostgreSQL aggregations and stable deterministic service methods.
- [x] Add bounded presets/custom ranges, store-local boundaries, previous-period logic, and currency validation.
- [x] Add overview, trend, product, category, order-summary, inventory-summary, and inventory-risk endpoints.
- [x] Replace dashboard placeholders with live Recharts visualizations and a dedicated analytics workspace.
- [x] Add backend calculation, validation, MVC-security, migration, PostgreSQL correctness/isolation, and boundary tests.
- [x] Add frontend service/page coverage for KPIs, comparison, custom dates, loading, empty, and error behavior.
- [x] Add deterministic seed/reset SQL, k6 analytics workload, Micrometer timers/error counters, and structured failure logs.
- [x] Run final Compose, E2E, rendered-browser, seed/reset, and k6 verification; record measured results before signoff.
- [x] Update architecture, database, API/OpenAPI, KPI, decision, testing, and README documentation.

### Phase 4 acceptance criteria

- Every financial KPI comes from paid order/payment records using decimal arithmetic and immutable line snapshots.
- All queries derive `store_id` from the authenticated context, use bounded filters, and avoid multiplying order totals across item joins.
- The UI displays actual API responses with honest zero, null, empty, loading, and failure semantics.
- Inventory coverage is explicitly a current-stock/selected-period-velocity estimate, not a forecast or historical stockout measure.
- Service contracts are directly reusable by typed Phase 5 tool adapters without duplicating SQL or metric formulas.

## Phase 5 — AI data analyst

- [x] Add a configurable mock/OpenAI provider boundary using Spring AI 2.0.1 `ChatClient` and tool calling.
- [x] Expose eight read-only, typed analytics tools that reuse Phase 4 services and receive tenant identity only through trusted server context.
- [x] Add deterministic response assembly so authoritative metrics, charts, tables, filters, and evidence originate only from validated tool results.
- [x] Persist owner-scoped conversations, bounded history, structured assistant payloads, and safe tool execution audits through Flyway V4.
- [x] Add authenticated query/list/detail endpoints with CSRF, request/result bounds, graceful provider errors, and cross-user/store isolation.
- [x] Add a responsive AI Copilot with conversation history, suggested prompts, retry/error/loading states, KPI cards, tables, and Recharts visualizations.
- [x] Add a version-controlled safety prompt, operational metrics, safe structured logging, provider timeouts, and tool-call limits.
- [x] Add 56 deterministic evaluation cases plus unit, MVC, PostgreSQL integration, frontend, and real-stack follow-up coverage.
- [x] Run Java 21 verification: 84 unit/web/migration/evaluation tests and 9 PostgreSQL 18 integration tests passed (93 total).
- [x] Run frontend strict TypeScript, zero-warning lint, 31 Vitest tests, production build, Compose health, and Playwright E2E verification.
- [x] Document architecture, tool contracts, API/OpenAPI, persistence, evaluation, decisions, configuration, limits, and known gaps.

### Phase 5 acceptance criteria

- Authenticated users can ask supported revenue, order, product, category, and inventory questions through the backend and continue an owned conversation.
- The provider can invoke only approved, bounded, read-only tools; it receives no SQL/database credential and cannot choose a store or user.
- Every authoritative number and chart point is assembled from typed Phase 4 results; provider-authored numbers are discarded.
- Conversation and audit data persists in PostgreSQL and every lookup is scoped by both authenticated user and store.
- The deterministic mock is the default, so local development and CI need no paid model; real OpenAI mode is explicit and isolated behind the same contract.
- Provider failure affects only the AI request. Commerce and direct analytics stay available.
- Phase 6 causal analysis, reports, proposals, approvals, and mutations are explicitly out of scope.

## Phase 5.5 — AI reliability hardening and Phase 6 readiness

- [x] Capture provider model/usage metadata and persist request-level success/failure telemetry through Flyway V5.
- [x] Externalize versioned pricing and label all calculated spend as estimated rather than billed cost.
- [x] Enforce transport connect/read/write/request timeouts and one bounded exponential-backoff retry while disabling stacked SDK retries.
- [x] Add mock-server faults for timeout, reset, 429, 500/502/503, malformed JSON, and interrupted bodies.
- [x] Prove AI outages return safe errors and leave product, inventory, order, and direct analytics paths independent.
- [x] Add a billable live-provider evaluation that is disabled unless `RUN_LIVE_AI_EVAL=true`, with request/token/cost limits and sanitized results.
- [x] Add versioned evidence/report contracts and referential validation without implementing Phase 6 analysis behavior.
- [x] Document the proposed report persistence, ownership, freshness, versioning, and retention model without adding speculative report tables.
- [ ] Run the live-provider evaluation with an explicitly approved API key and budget; behavior remains unverified until that opt-in run occurs.

Verification on 2026-10-22: 112 non-billable backend tests passed, the one live evaluation was skipped, 31 frontend tests plus type/lint/build passed, the backend image built, and the healthy local runtime applied Flyway V5.

### Phase 5.5 acceptance criteria

- Deterministic CI needs no API key and performs no billable call.
- Transient provider calls have at most two total attempts with bounded delay; malformed content fails without retry.
- Request telemetry contains no prompt/response body or credential and cannot make a successful AI response fail if telemetry persistence is unavailable.
- Provider-reported usage is authoritative where available; cost is an estimate from a versioned price configuration and is omitted for an unpriced model.
- Findings, hypotheses, recommendations, limitations, and evidence are structurally distinct and causality cannot be represented as a verified observation.
- Phase 6 services, endpoints, persistence, and business-analysis generation remain unimplemented.

## Phase 6 preflight — repository and architecture baseline

- [x] Audit the initial Git baseline for secrets, local environment files, generated output, and ignored dependencies before staging.
- [x] Confirm server-side report authorization: ADMIN can access all reports in the authenticated store; STAFF can access only reports they created; cross-store access is prohibited.
- [x] Confirm PostgreSQL relational metadata plus a versioned JSONB report body and retrieval-driven index design.
- [x] Set a configurable 90-day development retention default without automatic deletion or any claim of production-policy approval.
- [x] Retain deterministic mock evaluation as the required CI baseline and opt-in live-provider evaluation as unverified until explicitly approved and run.
- [x] Review analytics contracts, AI adapters, evidence validation, retry handling, telemetry, and tenant boundaries; no code-level blocker was identified.
- [x] Stop before Phase 6 implementation during preflight; implementation was separately authorized afterward.

## Phase 6 — AI Business Analyst and evidence-based decision intelligence

- [x] Implement paid-revenue comparison, product/category contribution, explicit residuals, and zero-baseline semantics.
- [x] Implement product-level symmetric price-volume decomposition with new/discontinued lifecycle effects and method limitations.
- [x] Implement current inventory, reserved stock, velocity, coverage, sales-change, low-stock, and excess-inventory diagnostics.
- [x] Implement bounded unit-sales, price, discount, and sales-velocity scenarios with transparent assumptions and optional gross profit.
- [x] Build a versioned report schema separating verified observations, calculations, hypotheses, recommendations, scenarios, and limitations.
- [x] Restrict model synthesis to allow-listed codes over aggregate signals; keep authoritative numbers and evidence server-owned.
- [x] Add Flyway V6 relational metadata + JSONB persistence, lifecycle transitions, optimistic locking, request idempotency, and retention marker.
- [x] Enforce ADMIN store-wide, STAFF creator-only, anonymous denial, and cross-store not-found behavior server-side.
- [x] Add report create/list/detail APIs and a React Business Insights workspace with history, charts, evidence, scenarios, and failure states.
- [x] Add the 44-case deterministic evaluation fixture plus calculation, schema, security, provider-failure, persistence, frontend, and E2E coverage.
- [ ] Run a billable real-provider Phase 6 evaluation; no authorization was given, so behavior remains unverified.
- [x] Record final complete backend, Compose, and E2E verification evidence before signoff.

### Phase 6 acceptance criteria

- Revenue contributions and price-volume/lifecycle components reconcile to the paid-order period change or expose a residual.
- The model cannot author financial values, evidence IDs, arbitrary causal claims, or operations.
- Every completed report passes referential validation and is an immutable point-in-time snapshot.
- Report creation is idempotent and failed provider calls leave explicit failed metadata without a fabricated document.
- Report access always derives tenant and role from `StoreContext`; UI visibility is not an authorization control.
- Scenarios are calculations with assumptions, never forecasts. Recommendations are advisory and require human approval.
- Phase 7 operational proposal/execution behavior is isolated from this Phase 6 report-generation contract.

## Phase 7 preflight — restock proposal architecture

- [x] Review Phase 6 persistence, authorization, evidence, provider isolation, AI tool scope, and analytics correctness.
- [x] Verify the current Git baseline and reconcile the earlier no-HEAD observation with the actual repository state.
- [x] Define the proposal state machine, human approval boundary, freshness checks, idempotent execution, role authorization, audit, and failure recovery.
- [x] Document required dependencies, non-goals, retrieval-driven indexes, API shape, and test strategy.
- [x] Reconcile interrupted Phase 6 `PENDING`/`RUNNING` reports with ownership leases and bounded scheduled recovery.
- [x] Validate relational report metadata against JSONB identity/ownership/version/period metadata on retrieval.
- [x] Receive explicit authorization to begin Phase 7 implementation.

## Phase 7 — AI Operations Agent and safe inventory execution

- [x] Persist relational proposals, append-only transition events, and execution attempts through Flyway V8; add proposal-generation model telemetry through the additive Flyway V9 migration.
- [x] Implement `PROPOSED → APPROVED → EXECUTING → EXECUTED` plus reject, cancel, stale, failed, and lease-recovery paths.
- [x] Enforce creator-only STAFF visibility, store-wide ADMIN visibility, ADMIN-only approval/execution, and cross-store not-found behavior.
- [x] Generate evidence-linked proposals from completed reports using a bounded provider selector and deterministic quantity calculation.
- [x] Require explicit physical-receipt confirmation and revalidate report, evidence hash, product, inventory snapshot, analysis age, and current risk.
- [x] Make receipt execution command-idempotent and atomically persist inventory, adjustment, proposal, audit, attempt, and outbox state.
- [x] Add the connected React Operations workspace with generation, evidence, decisions, receipt confirmation, warnings, and history.
- [x] Add migration, AI-output, state, authorization, freshness, idempotency, concurrency, audit, outbox, recovery, and frontend coverage.
- [x] Verify Java 21 backend (185-test full gate plus 2 focused final acceptance tests; 1 live test skipped), frontend checks (41 tests), healthy Compose/Flyway V9, and 2 Playwright workflows.

### Phase 7 implementation acceptance criteria

- Approval and execution are separate server-authorized commands; neither the model nor UI visibility is an authorization boundary.
- Every execution revalidates the exact approved proposal, current product/inventory version, report evidence, role, and freshness.
- A replay or concurrent execution can produce at most one stock increment, inventory audit, proposal completion, and outbox event.
- Successful inventory, proposal, audit, attempt, and outbox writes commit atomically; failures leave no partial operational effect.
- Expired execution claims and failed attempts have bounded, observable recovery without infinite retry or blind side-effect repetition.
- Phase 7 uses current deterministic inventory evidence only; historical forecasting and causal inference remain out of scope.

Architecture and implemented invariants are documented in [phase7-architecture.md](phase7-architecture.md).

### Phase 7 reliability review

- [x] Bound the affected workflow tests and add delayed PostgreSQL blocker/wait
  plus JVM thread diagnostics.
- [x] Repeat the stale-inventory class under controlled conditions and document
  that the earlier stall's root cause is not established from available evidence.
- [x] Confirm the outbox currently has no relay/consumer and distinguish durable
  transactional recording from future at-least-once delivery.
- [x] Add route-level lazy loading and reduce the initial production JavaScript
  from 777.47 kB/225.15 kB gzip to 257.19 kB/79.55 kB gzip.
- [x] Strengthen opt-in live evaluation tool-argument and hard cost-limit checks
  without executing the billable suite.

Findings and priorities are documented in
[phase7-reliability-review.md](phase7-reliability-review.md).

## Phase 8 — Production hardening, reliability, performance, and security

- [x] Add request correlation, safe structured completion logs, route/status
  latency meters, and readiness-based Compose/CI checks.
- [x] Add bounded login-failure throttling, HTTP body/header limits, expanded
  operations MVC security checks, secret scanning, dependency audit, and
  scheduled dependency updates.
- [x] Add concurrent same-key order and payment/cancellation race tests;
  repeatedly exercise stale restock validation with bounded waits.
- [x] Investigate the earlier stale test stall without claiming an unproven
  deadlock; preserve diagnostics and bound related PostgreSQL integration tests.
- [x] Confirm that outbox events are atomic durable records only and defer a
  relay/broker until a named consumer exists.
- [x] Add a reproducible mixed k6 workload, capture endpoint p50/p95,
  throughput/error rate, and inspect a representative PostgreSQL plan.
- [x] Protect route-splitting gains with a production bundle budget and retain
  the 257.19 kB initial entry.
- [x] Add negative Playwright coverage for idempotency conflict, insufficient
  stock, invalid transition, and cross-store isolation.
- [x] Correct browser UUID generation for non-secure local origins and verify
  all three real-stack workflows.
- [x] Keep deterministic AI evaluation mandatory and live provider evaluation
  disabled because no billable run was authorized.
- [x] Harden CI with job timeouts, readiness checks, secret/dependency checks,
  concurrency cancellation, and weekly Dependabot coverage.
- [x] Publish Phase 8 performance, security, reliability, verification, and
  engineering metrics documentation.

### Phase 8 acceptance criteria

- Business mutations retain server-side authorization, explicit approval where
  consequential, transactionality, idempotency, and bounded concurrency tests.
- AI/provider failure never gates product, inventory, order, or direct
  analytics workflows; offline deterministic evaluation remains the CI gate.
- Local measurements are reproducible and clearly separated from production
  SLO/capacity claims.
- Health, logs, correlation IDs, and metrics are useful without recording
  credentials, prompt bodies, or business payloads.
- Security findings and deployment limitations are severity-ranked and are not
  presented as a penetration test or production approval.
- No Phase 9 cloud deployment or paid provider evaluation is started.

## Phase 9 — Cloud deployment, CI/CD, and portfolio launch

### Stage A — Deployment preparation

- [x] Compare three cost-conscious AWS hosting options and document the managed
  S3/CloudFront + ECS/Fargate + private RDS recommendation and trade-offs.
- [x] Publish an itemized, assumption-based $88–95/month cost estimate, charge
  risks, budget alerts, stop/teardown guidance, and an explicit approval gate.
- [x] Add a strict Spring `prod` profile, bounded database pool/timeouts,
  graceful shutdown, secure cookies, proxy handling, hidden health details, and
  server-side public-registration control.
- [x] Prepare validated Terraform for private frontend/database origins, HTTPS,
  ECS, RDS backups, ECR, least-privilege GitHub OIDC, logs, alarms, and budgets.
- [x] Add manual, immutable-SHA release verification and deployment workflows;
  do not deploy unreviewed changes or use long-lived AWS credentials.
- [x] Add reproducible synthetic seed/bootstrap/workflow tools with mock AI and
  no real customer, payment, supplier, credential, or document data.
- [x] Harden frontend routing/cache/security headers and preserve the Phase 8
  production bundle budgets.
- [x] Create the cost, deployment, operations, demo, interview, resume, and
  Stage A verification documentation and recruiter-oriented README.
- [x] Capture local working-application screenshots from the verified synthetic
  environment; do not present generated mockups as product evidence.
- [x] Re-run Java 21/PostgreSQL, frontend, Compose, Playwright, Terraform,
  workflow, script, container, and security checks and record exact evidence.

### Stage A acceptance criteria

- Infrastructure and delivery code can be inspected and validated locally
  without AWS credentials, a paid provider, a public endpoint, or a domain.
- Production defaults keep registration and paid AI disabled; PostgreSQL is
  private in the proposed network and secrets remain runtime-only.
- Monitoring covers availability, 5xx, latency, database pressure/storage,
  report/receipt/provider failures, recovery signals, token/cost logs, and spend.
- CI remains deterministic and non-billable; CD requires an explicit dispatch,
  typed confirmation, protected environment approval, and reviewed full SHA.
- Documentation distinguishes verified local behavior, prepared infrastructure,
  estimated charges, and unverified live/cloud behavior.

### Stage B — Approved deployment

- [ ] Obtain explicit approval for the architecture, account, region, domain,
  public exposure, $88–95/month estimate, budget, and retention policy.
- [ ] Provision the reviewed resources, configure secrets/TLS, run migrations,
  seed only synthetic data, and disable registration after controlled bootstrap.
- [ ] Run the post-deployment smoke/security/E2E checklist and verify alarms,
  logs, rollback, and database restore behavior with recorded evidence.
- [ ] Add a live URL only after HTTPS and all applicable workflows pass.

Stage B is intentionally not started by Stage A completion.
