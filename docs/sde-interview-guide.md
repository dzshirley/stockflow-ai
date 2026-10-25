# StockFlow AI — General SDE Interview Guide

These answers describe implemented behavior. They intentionally distinguish
local verification, prepared cloud architecture, and unperformed deployment.

## Why Java and Spring Boot?

The domain has transactions, authorization, validation, scheduled recovery,
typed integrations, and concurrency. Java gives explicit types and mature
concurrency/database tooling; Spring Boot supplies cohesive security, MVC, JPA,
Flyway, Actuator, Micrometer, and test integration. The trade-off is higher
memory/startup cost than a smaller runtime, which is reflected in the prepared
Fargate sizing and local measurements.

## Why a modular monolith?

Orders, reservations, inventory adjustments, proposal state, audits, and outbox
rows often need one database transaction. A package-by-feature monolith keeps
those invariants local and deploys one unit while retaining clear auth, product,
inventory, order, analytics, AI, analysis, and operations boundaries. Services
would add network failure, distributed transactions, deployment overhead, and
cost before scale or team boundaries justify them.

## How does the frontend communicate with the backend?

The React SPA uses a typed fetch wrapper with JSON, same-origin `/api` paths,
cookie credentials, CSRF bootstrap, Problem Details errors, and query-cache
invalidation after mutations. In the prepared AWS design, CloudFront serves S3
assets and forwards `/api/*` to the ALB, preserving one browser origin and
avoiding a permissive CORS policy.

## How are transactions handled?

Controllers validate DTOs and call services. Transactional service/persistence
methods commit related database effects together. Order creation commits the
idempotency gate, order, lines, inventory reservations, audit/result, and outbox
or rolls everything back. Restock receipt commits inventory, adjustment,
proposal transition, attempt, audit event, and outbox atomically. Failed attempt
metadata that must survive rollback is recorded in a separate short transaction.

## How do you prevent overselling?

The order service normalizes duplicate lines, sorts product UUIDs to keep lock
order deterministic, and locks/reserves inventory through PostgreSQL. Conditional
updates reject quantities that would make available inventory negative. A
Testcontainers race for the last item proves only one transaction wins.

## What makes order creation idempotent?

The client sends an `Idempotency-Key`. The store-scoped database gate stores a
hash of the normalized request and the durable result. A replay with the same
payload returns the original order; the same key with different content returns
conflict. Concurrent same-key requests serialize on the gate and create one
business effect.

## How are concurrent terminal requests handled?

Payment and cancellation lock the order row and validate the current state.
The Phase 8 integration race proves one terminal transition wins and inventory
plus outbox effects match that outcome. Proposal execution also combines a row
claim/lease, expected optimistic version, unique command ID, and inventory
revalidation.

## What is the transactional outbox?

An outbox event row is inserted in the same transaction as the business change,
so a commit cannot contain one without the other. StockFlow currently has no
consumer or relay, so it guarantees durable recording—not external delivery.
If a consumer appears, a relay should claim bounded batches with `SKIP LOCKED`,
retry with backoff, and deliver at least once; consumers must deduplicate event
IDs. Exactly-once broker delivery is not claimed.

## How does AI tool calling work?

The optional provider receives a bounded prompt and can invoke eight typed,
read-only analytics adapters. The server injects authenticated store/user
context; no tool accepts caller/model store IDs, arbitrary SQL, or mutations.
The deterministic mock exercises the same orchestration path in ordinary CI.
Provider retries are limited to classified transient failures and two attempts.

## Why compute numbers outside the LLM?

Language models are probabilistic and weak authorities for financial arithmetic.
SQL/Java services calculate paid revenue, comparisons, decomposition, velocity,
coverage, and scenarios with explicit definitions and BigDecimal. The response
assembler discards provider-authored authoritative values and builds metrics,
charts, filters, and evidence from typed results. This makes tests deterministic
and unsupported causal claims structurally distinguishable.

## How is tenant isolation enforced?

Authentication creates an `AuthenticatedUser` whose membership supplies
`userId`, `storeId`, and role. `StoreContextProvider` derives scope server-side;
API/model inputs never choose a store. Repository lookups include store ID, and
STAFF report/proposal queries also include creator ID. Cross-store identifiers
return not found. MockMvc and PostgreSQL tests cover anonymous, role, creator,
and cross-store cases.

## How does the proposal state machine work?

The success path is `PROPOSED → APPROVED → EXECUTING → EXECUTED`, with reject,
cancel, stale, and failed paths. Only ADMIN can approve or execute. Approval does
not change stock. Execution requires physical receipt confirmation and rechecks
proposal/report/evidence/product/inventory versions and freshness. The model can
suggest candidates but cannot transition state or touch inventory.

## What happens when a worker or process crashes?

Report generation and proposal execution use owner/lease metadata. Recovery
claims expired rows in bounded batches with row locks and `SKIP LOCKED`. For
inventory execution it first looks for the unique committed adjustment/result:
if found, it finalizes the proposal; otherwise it returns the claim for explicit
revalidation. It never blindly repeats a side effect.

## How are provider/network failures handled?

Connect/read/write/request timeouts are bounded. Timeout, reset, 429, and
500/502/503 scenarios have mock-server coverage; transient failures get at most
one retry with exponential backoff. Malformed/interrupted output fails closed.
Safe error codes and privacy-minimized telemetry record provider/model, latency,
tool counts, token usage, estimated cost, and status without prompts or keys.

## How was performance measured?

The checked-in k6 profile creates a synthetic tenant, 100 paid orders, a report,
and an approved proposal, then mixes catalog, inventory, analytics, order,
report, proposal, and receipt operations. Five VUs for 15 seconds produced 4,316
requests at 225.07 requests/second, 0% errors, 4.32 ms p50, 26.74 ms p95, and
83.22 ms p99 locally. These are reproducible development observations, not SLOs
or production capacity claims. Query-plan and container point samples are
documented separately.

## How does CI/CD work?

CI runs Java 21 Maven verification including Testcontainers, strict TypeScript,
ESLint, Vitest, production build/bundle budgets, real-stack Chromium E2E, secret
scanning, and frontend dependency audit. Prepared CD is manual only: it accepts
a reviewed full SHA, reruns the release gate, requires a protected GitHub
environment, assumes a least-privilege AWS role through OIDC, pushes an immutable
image, publishes cache-controlled assets, updates ECS, waits for stability, and
checks HTTPS health. It has not run remotely or deployed AWS resources yet.

## What would change at 100x usage?

Measure first. Likely changes are shared sessions and distributed throttling,
multiple autoscaled tasks, RDS Multi-AZ/a larger instance, pool sizing based on
database capacity, slow-query/index work, and read replicas for proven read
pressure. A relay/queue is justified only by a real async consumer. I would not
start with microservices or Redis because neither fixes an identified current
bottleneck.

## What was a meaningful debugging lesson?

An intermittent long integration test lacked blocker evidence, so I did not
label it a deadlock. I added bounded JUnit/future/database waits and delayed
PostgreSQL/JVM diagnostics, isolated scheduled recovery from manual recovery,
and repeated the affected cases. The final suite passed, but documentation
retains uncertainty rather than turning correlation under Docker contention
into an unsupported root-cause claim.
