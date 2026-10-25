# System Architecture

## Context and containers

```mermaid
flowchart LR
    User["Admin or Staff user"] -->|HTTPS| Web["React + TypeScript SPA"]
    Web -->|REST/JSON| API["Spring Boot modular monolith"]
    API -->|JPA / SQL| DB[(PostgreSQL)]
    API -->|same transaction| Outbox["PostgreSQL outbox_events"]
    Scheduler["Order expiration scheduler"] -->|short isolated transactions| API
    Outbox -. later relay .-> MQ[(RabbitMQ)]
    API -->|typed prompts and tools| LLM["LLM provider or local mock"]
    API -. optional measured cache .-> Redis[(Redis)]
```

Phase 2 expires orders with an in-process scheduled scanner and persists order events atomically in PostgreSQL. RabbitMQ and an outbox relay remain a later addition when an actual asynchronous consumer exists; this avoids operating an unused broker without weakening event durability. Redis is also absent until a measured need exists. An LLM outage disables synthesis, not commerce or deterministic analytics.

The outbox currently guarantees atomic, durable event recording only. There is
no relay or consumer, so it does not yet guarantee delivery. A future relay must
provide bounded claiming/retry and consumers must deduplicate event IDs for
at-least-once delivery; see the [Phase 7 reliability review](phase7-reliability-review.md).

Phase 3 keeps the browser deliberately thin: it owns navigation, forms, request state, and presentation, while the backend remains authoritative for tenant scope, permissions, validation, prices, inventory, state transitions, and idempotency.

Phase 4 keeps analytics in the same modular monolith. Controllers accept only bounded reporting filters; `AnalyticsPeriodResolver` converts store-local dates to UTC half-open instants; `AnalyticsService` owns formulas/comparisons and exposes future AI-ready operations; `AnalyticsQueryRepository` pushes grouping and filtering into PostgreSQL. No analytics endpoint accepts a store ID.

Phase 5 adds an AI interpretation layer without changing that correctness boundary. A deterministic mock is the default; the optional Spring AI/OpenAI adapter and the mock both invoke the same allow-listed tool adapter. Final public metrics and visualizations are assembled server-side from typed analytics results, never from model prose.

Phase 6 implements evidence-backed analysis inside the monolith. Reports use PostgreSQL relational metadata plus a validated, versioned JSONB document. Server-derived authorization allows store ADMIN users to access all reports in their store and STAFF users to access only reports they created; every lookup remains store-scoped. A configurable 90-day retention value is a development default only, with no automatic deletion until lifecycle, audit, backup, and compliance policies are approved.

## Backend module boundaries

```mermaid
flowchart TB
    HTTP["Controllers + validation"] --> Auth[auth]
    HTTP --> Product[product]
    HTTP --> Inventory[inventory]
    HTTP --> Order[order]
    HTTP --> Analytics[analytics]
    HTTP --> AI[ai]
    HTTP --> Analysis[deterministic analysis + evidence reports]
    Auth --> Store[store + authenticated context]
    Product --> Store
    Inventory --> Product
    Order --> Inventory
    Analytics --> Order
    Analytics --> Inventory
    AI --> Analytics
    AI --> AIStore[conversation + tool audit]
    Analysis --> Analytics
    Analysis --> AI
    Analysis -. future persistence .-> ReportStore[PostgreSQL metadata + JSONB]
    AIStore --> Store
    Auth --> Common[common errors, IDs, time]
```

Dependencies flow through public service interfaces; controllers do not access repositories from another feature. Cross-feature database changes are coordinated by one application transaction.

## Phase 2 request and ownership flow

1. `GET /api/auth/csrf` creates a CSRF cookie and returns the request token.
2. Registration atomically creates a user, store, and ADMIN membership; login selects an active membership and rotates/establishes the server session.
3. The session principal contains `userId`, `storeId`, and role. `StoreContextProvider` converts it to a domain context; controllers never accept `storeId` from JSON, query parameters, or headers.
4. Controllers validate transport DTOs and call feature services. Services use repositories whose tenant-owned lookups include both resource ID and authenticated store ID.
5. Method security restricts administrative operations such as member creation and product archival; authenticated ADMIN and STAFF users can run normal commerce operations.

## Phase 2 order transaction

```mermaid
sequenceDiagram
    actor Client
    participant API as OrderController
    participant O as OrderService transaction
    participant K as Idempotency gate
    participant I as Inventory
    participant DB as PostgreSQL

    Client->>API: POST /orders + Idempotency-Key
    API->>O: authenticated StoreContext + validated lines
    O->>K: insert-on-conflict, then lock key row
    K-->>O: new gate or prior order
    O->>I: reserve products in deterministic UUID order
    I->>DB: conditional UPDATE available >= quantity
    O->>DB: order + snapshot items + audit + outbox
    O->>K: attach order ID to key
    O-->>Client: created order or idempotent replay
```

Order creation is one database transaction. Product IDs are sorted before reservation to keep lock order deterministic. Conditional SQL updates make PostgreSQL the oversell boundary. Payment, cancellation, and expiration first take a pessimistic lock on the order, verify its state, then consume or release reservations in the same transaction. Expiration processes each order in its own `REQUIRES_NEW` transaction so one conflict cannot roll back a batch.

## Phase 3 browser request flow

1. `main` mounts one `QueryClient`, `BrowserRouter`, `AuthProvider`, and toast provider.
2. `AuthProvider` restores `/api/auth/me`; protected/public route guards redirect without rendering private screens. A centralized 401 event clears tenant query data and marks the session expired.
3. The typed API client sends cookies on every call and bootstraps the current CSRF header immediately before each mutation. Problem Details responses become one structured `ApiError` shape.
4. Page components use TanStack Query for remote state and feature forms for local drafts. Successful mutations invalidate every affected aggregate—for example product creation refreshes product and inventory caches.
5. The production Nginx container serves SPA fallbacks and proxies `/api` to Spring Boot. Native Vite development uses its backend proxy; `VITE_API_BASE_URL` supports an explicit API origin when required.
6. Order creation generates one cryptographically random draft key, disables repeat submission while pending, and sends `Idempotency-Key`; PostgreSQL still performs the decisive availability check and reservation transaction.

## Phase 4 analytics flow

```mermaid
sequenceDiagram
    actor User
    participant UI as React analytics page
    participant API as AnalyticsController
    participant S as AnalyticsService
    participant R as AnalyticsQueryRepository
    participant DB as PostgreSQL

    User->>UI: Select preset or custom local dates
    UI->>API: GET analytics endpoint (no store ID)
    API->>S: StoreContext + validated filters
    S->>S: Resolve store-local [start, end) and comparison
    S->>R: Store UUID + UTC instants + bounded limit
    R->>DB: Aggregate paid orders/items with GROUP BY/CTE
    DB-->>R: Decimal aggregates and bounded rows
    S-->>API: Typed metrics, periods, null semantics
    API-->>UI: JSON
    UI-->>User: KPI, chart, table, or honest empty/error state
```

The overview query aggregates order totals at order grain and units in a separate item aggregate, preventing multi-line joins from inflating revenue or order count. Trend queries first create one fact row per order and only then bucket the data. Product/category queries use immutable order-item snapshots. Current inventory is deliberately separate from selected-period velocity.

Read-only analytics do not take business locks and never mutate commerce state. A request sees PostgreSQL's normal `READ COMMITTED` statement snapshots; a payment either commits as a complete paid order with consumed reservations or remains invisible. Exact agreement between several concurrently issued dashboard requests is not guaranteed if a payment commits between them, so the UI treats each response as its own timestamp-free live read. Product, inventory, order, payment, and cancellation mutations invalidate the shared TanStack Query `analytics` key so dashboard and workspace reads cannot reuse a stale pre-mutation snapshot.

## Phase 5 AI request flow

```mermaid
sequenceDiagram
    actor User
    participant UI as React AI Copilot
    participant API as AI Controller
    participant C as Conversation service
    participant O as Orchestrator
    participant M as Mock or Spring AI provider
    participant T as Authorized tools
    participant D as Phase 4 AnalyticsService
    participant DB as PostgreSQL

    User->>UI: Natural-language question
    UI->>API: POST /api/ai/query + session + CSRF
    API->>C: Save normalized user message under user/store ownership
    API->>O: Bounded history + trusted StoreContext
    O->>M: Versioned policy prompt and approved tool definitions
    M->>T: Bounded typed tool calls (no tenant parameters)
    T->>D: Metrics/decomposition
    D->>DB: Store-scoped deterministic aggregate
    DB-->>D: Typed decimal results
    D-->>T: Verified structured evidence
    T-->>O: Results + safe execution IDs
    O->>O: Deterministic response assembly
    O->>C: Save assistant payload
    API-->>UI: Metrics, charts, tables, evidence, limitations
```

The application passes `StoreContext` to tools through trusted server-side `ToolContext`; tool schemas do not expose `storeId`, `userId`, SQL, or credentials. Conversation lookups require both authenticated IDs. The conversation row has an optimistic version, so simultaneous updates cannot silently overwrite one another; a losing write returns `409 CONCURRENT_MODIFICATION`. Tool audit records use short independent transactions so failed calls remain observable. The actual analytics reads retain Phase 4's `READ COMMITTED` behavior and remain read-only.

## Phase 6 business-analysis boundary

The `analysis` package now contains deterministic calculations, store-scoped query aggregation, bounded planning, report orchestration, evidence validation, persistence, and authenticated list/detail/create APIs. The frontend exposes the same boundary at `/business-insights`.

Report creation snapshots verified metrics and provenance into a schema-versioned JSONB document while projecting ownership, status, period, freshness, version, retention marker, and model metadata into relational columns. ADMIN list queries use store scope; STAFF queries use both store and creator scope. Optimistic status transitions prevent conflicting completion, and idempotency prevents duplicate generation on request replay. Retrieval indexes deliberately exclude speculative JSONB indexing.

Deterministic analytics own all numbers and chart points. The provider receives only a bounded aggregate signal bundle and can select only allow-listed hypothesis/recommendation codes. Deterministic mock evaluation remains the required CI gate. Live-provider evaluation stays disabled unless explicitly enabled and budgeted, so real-provider structured selection remains unverified. See [business analysis design](business-analysis-design.md).

## Phase 7 operations boundary

Phase 7 is implemented inside the modular monolith under `com.stockflow.operations`. PostgreSQL coordinates a versioned proposal state machine and bounded execution leases. Proposal generation is read-only: a provider may select only IDs from server-authorized inventory-risk candidates, while deterministic code calculates quantities. Proposal, ADMIN approval, and explicit ADMIN receipt confirmation are distinct commands.

Execution reauthorizes the store and role and revalidates the completed report, recommendation/evidence hash, report age, active product, exact inventory version/time/values, positive bounded quantity, and current deterministic risk. The expected-version inventory increment, inventory adjustment linked by unique command ID, proposal completion, workflow event, successful attempt, and outbox event commit in one transaction. A replay returns the persisted result; a competing command loses the locked state transition.

No provider call belongs in approval or execution. A provider outage cannot block an already-created proposal, and a model never supplies tenant identity, approval, quantity changes after approval, or mutation authorization. Historical inventory forecasting, causal inference, supplier ordering, queues, Redis locks, and a new deployment remain outside scope. See [Phase 7 architecture](phase7-architecture.md) and [Phase 6 readiness review](phase7-readiness-review.md).

## Phase 8 production-hardening boundary

Every HTTP request receives a validated caller-supplied or server-generated
correlation ID. The response echoes `X-Request-ID`; MDC carries the ID only for
the request lifetime. A completion log and Micrometer timer record method,
matched route, status, and duration without query strings, request/response
bodies, cookies, credentials, or tenant business data. Actuator readiness is
the dependency-aware container/CI gate, while `/api/health` remains the stable
application health contract.

Login throttling is a bounded in-process defense suitable for the current
single-instance runtime, not a distributed production rate limiter. PostgreSQL
continues to own correctness: no JVM rate-limit, request-ID, metric, or retry
mechanism participates in commerce transactions. Transactional retries remain
explicit idempotent client replays; only read-only provider transport attempts
use bounded automatic backoff.

The frontend keeps route-level code splitting and enforces raw-minified budgets
of 300 KiB for the initial entry and 400 KiB for any JavaScript chunk. UUID
generation uses Web Crypto `randomUUID` when present and a version/variant-safe
`getRandomValues` fallback for non-secure local origins. See the
[performance report](performance-report.md), [security review](security-review.md),
and [reliability review](reliability-review.md).

## Repository layout

```text
backend/                 Spring Boot application and Maven wrapper
  src/main/java/com/stockflow/
    analytics/            Deterministic periods, metrics, SQL aggregation, REST DTOs
    ai/                   Provider gateway, tool adapter, orchestration, persistence, REST DTOs
    analysis/             Deterministic business analysis, evidence reports, persistence, APIs
  src/main/resources/db/migration/
  src/main/resources/prompts/       Version-controlled model policy prompts
  src/test/
frontend/                React/Vite application and tests
  src/app/               Query-client configuration
  src/components/        Shared shell, feedback, modal, toast, status UI
  src/features/          Auth, product, and inventory feature logic/forms
  src/pages/             Route-level screens, including AI Copilot and Business Insights
  src/services/          Typed HTTP boundary, including conversation APIs
  e2e/                   Playwright real-stack workflows
infra/docker/            Container build/runtime assets
infra/aws/               Validated Stage A Terraform; never apply without approval
scripts/seed/              Deterministic analytics demo seed/reset SQL
scripts/performance/       k6 analytics and Phase 8 mixed API workloads
docs/                    Requirements, architecture, contracts, decisions
.github/workflows/       CI plus approval-gated release/deployment definitions
docker-compose.yml       Local integrated environment
```

## Phase 9 prepared deployment architecture (not provisioned)

Stage A keeps the modular monolith intact and prepares one same-origin public
surface. CloudFront serves the React build from a private S3 origin and forwards
uncached `/api/*` traffic over TLS to an ALB. The ALB accepts ingress only from
the AWS-managed CloudFront origin prefix list and requires a random origin
header; it forwards to one Fargate task whose security group has no direct
internet ingress. RDS PostgreSQL is Single-AZ, encrypted, non-public, backed up
for seven days, and reachable only from the backend security group.

One task is a deliberate portfolio-demo constraint because sessions and login
throttling are process-local. Its public IP supplies outbound ECR, CloudWatch,
and optional provider connectivity without a continuously billed NAT Gateway;
the task remains unreachable from the internet by security-group policy. Before
horizontal scaling, introduce an explicitly reviewed shared-session mechanism
and shared or edge throttling. No queue, relay, cache, broker, or worker is
deployed because the application has no named outbox consumer.

The Spring `prod` profile requires runtime database credentials, TLS database
transport, secure cookies, trusted forwarded headers, bounded Hikari connection
settings, graceful shutdown, Flyway migration/validation, and hidden health
details. Registration and paid AI are disabled by default. The container
filesystem is read-only except for `/tmp`, and ECS injects the RDS-managed
secret through its execution role.

Deployments are manual and commit-addressed. A reusable GitHub Actions gate
re-runs backend, frontend, integration, migration, bundle, and container checks;
the protected production job then assumes a scoped AWS role via OIDC, publishes
immutable artifacts, registers a new ECS task revision, waits for
circuit-breaker-protected health, updates the private S3 origin with explicit
cache headers, and invalidates CloudFront. Application rollback selects an
earlier immutable task and S3 version. Flyway remains forward-only and can
require forward repair or an approved snapshot restore.

The detailed network diagram, compared options, and limitations are in
[cloud-architecture.md](cloud-architecture.md). This describes prepared code,
not a live environment, and authorizes no Stage B AWS action.
