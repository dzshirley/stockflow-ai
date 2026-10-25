# Engineering Decision Log

## ADR-001 — Modular monolith first

- **Decision:** Use one Spring Boot deployment organized by feature packages and one React application.
- **Alternatives:** Microservices; a single layer-oriented package tree.
- **Why:** Domain boundaries remain visible while local transactions, deployment, debugging, and portfolio comprehension stay simple.
- **Trade-offs:** Modules are not independently scalable and require discipline to prevent cross-package coupling.
- **Future:** Extract a service only after measured scaling, ownership, or availability needs justify distributed complexity.

## ADR-002 — PostgreSQL is the system of record with Flyway-owned schema

- **Decision:** PostgreSQL stores transactional and report state; versioned Flyway SQL is authoritative and Hibernate validates rather than creates schema.
- **Alternatives:** Hibernate auto-DDL; a document database.
- **Why:** Relational constraints and transactions match inventory/orders, while migrations are reviewable and repeatable.
- **Trade-offs:** Schema changes require deliberate migrations and PostgreSQL-aware integration testing.

## ADR-003 — Database-enforced inventory consistency

- **Decision:** Inventory reserves, releases, consumes, and manual adjustments use atomic conditional updates inside a transaction and validate affected-row count.
- **Alternatives:** Read-then-write checks; JVM locks; Redis distributed locks.
- **Why:** The database is the concurrency boundary across application instances and prevents overselling without extra infrastructure.
- **Trade-offs:** Contention concentrates on hot SKUs; retry and lock-duration discipline are required.

## ADR-004 — Idempotency plus transactional outbox

- **Decision:** Client idempotency keys protect order creation. Domain state and outbox events commit in one database transaction. Phase 2 persists the outbox but defers the relay and RabbitMQ until a consumer is implemented.
- **Alternatives:** Publish directly inside a transaction; distributed transaction; no durable events.
- **Why:** This avoids a committed-order/missing-message gap without two-phase commit.
- **Trade-offs:** Current events are durable but not yet delivered externally. Once a relay exists, consumers must be idempotent and delivery will be at least once, not exactly once.

## ADR-005 — Server-controlled AI tools

- **Decision:** One orchestrator may call allow-listed, typed, bounded services. Store identity comes only from authentication. Important numeric results are deterministic; action DTOs are schema validated.
- **Alternatives:** Arbitrary SQL generation; direct model-to-database access; multiple autonomous agents.
- **Why:** Authorization, numeric correctness, observability, and failure isolation outweigh unconstrained flexibility.
- **Trade-offs:** Supported questions expand only when tools/analyses are deliberately added.

## ADR-006 — Approval is a stateful domain workflow

- **Decision:** AI operations create immutable-evidence proposals with explicit states; approval triggers permission and stale-data revalidation before idempotent execution through domain services.
- **Alternatives:** Execute from a tool call; store a boolean approval flag.
- **Why:** State transitions, audit history, rejection, retry, and race behavior must be explicit.
- **Trade-offs:** More persistence and workflow code, but materially safer operations.

## ADR-007 — No Redis in the foundation

- **Decision:** Do not add Redis until a measured cache, rate-limit, or coordination requirement exists.
- **Alternatives:** Include Redis preemptively.
- **Why:** PostgreSQL and application-local behavior cover current needs; unused infrastructure increases failure modes.
- **Trade-offs:** A later requirement may add operational setup and cache invalidation design.

## ADR-008 — Current stable foundation with pinned build inputs

- **Decision:** Target Java 21 and Spring Boot 4.1.1; target React 19.3 and Vite 8.3 with a supported modern Node runtime. Pin direct dependency versions and commit generated lockfiles once npm is available.
- **Alternatives:** Older long-lived versions; floating version ranges.
- **Why:** The brief asks for current compatible stable versions. Pinning makes CI repeatable.
- **Trade-offs:** New major versions have a smaller ecosystem knowledge base; dependencies still need routine security updates.

## ADR-009 — Server sessions, CSRF, and tenant-bearing principals

- **Decision:** Use BCrypt-hashed passwords, server-side HTTP sessions, session fixation protection, cookie CSRF tokens, and a principal containing immutable user/store/role identifiers.
- **Alternatives:** Browser-stored bearer JWTs; request-supplied store headers; disabling CSRF for JSON endpoints.
- **Why:** Sessions make revocation and browser credential storage straightforward. Requiring the returned CSRF token protects every state-changing endpoint, including login/registration. A server-derived store eliminates a broad class of tenant-spoofing bugs.
- **Trade-offs:** Horizontal scaling later needs sticky sessions or a shared session store, and browser clients must perform the CSRF bootstrap flow.

## ADR-010 — Serialized order transitions and deterministic reservation order

- **Decision:** Lock an order row pessimistically before terminal transitions, use a store-scoped idempotency row lock during creation, and reserve multi-line products in sorted UUID order.
- **Alternatives:** Unlocked read-then-write transitions; JVM mutexes; distributed locks.
- **Why:** Database locks coordinate every application instance. Consistent product order reduces deadlock risk, and short transaction boundaries make lock duration predictable.
- **Trade-offs:** Hot orders/SKUs serialize and may need bounded retry behavior under sustained contention. Repository bulk updates must not clear the whole persistence context because that would detach the locked aggregate before its state transition is flushed.

## ADR-011 — Server state in TanStack Query, security state in one auth provider

- **Decision:** Centralize cookie/CSRF/error behavior in one typed API client, keep the current principal in a dedicated auth query/provider, and use feature-scoped TanStack Query keys for all other server state. Mutations invalidate every affected aggregate.
- **Alternatives:** Duplicate `fetch` and auth handling in pages; mirror backend resources in a client state store; optimistic inventory/order writes.
- **Why:** Server data has independent freshness and failure semantics. Central 401 handling prevents stale tenant data surviving session expiry, while targeted invalidation keeps product, inventory, and order views coherent without pretending the browser won a concurrency race.
- **Trade-offs:** Cross-feature mutations must explicitly invalidate related keys. This was caught by E2E coverage when initial inventory briefly remained cached after product creation.

## ADR-012 — Backend idempotency is authoritative; the UI is defensive

- **Decision:** Generate one cryptographically random key per order draft, send it through `Idempotency-Key`, and disable the submit control while pending. Do not implement client-side locks or optimistic reservations.
- **Alternatives:** Rely only on button disabling; generate a new key for every retry; reserve stock in browser state.
- **Why:** UI defenses improve experience but cannot coordinate tabs, retries, network failures, or multiple application instances. The database idempotency gate and conditional inventory update provide the correctness boundary.
- **Trade-offs:** The browser must preserve the key while a draft is retried, and conflict responses remain possible because displayed availability is only a snapshot.

## ADR-013 — On-demand deterministic analytics over immutable facts

- **Decision:** Calculate Phase 4 metrics with bounded, store-scoped PostgreSQL aggregation queries over immutable payment and order-line snapshots. Keep date-range resolution and formulas in a typed service layer; do not add an OLAP service, cache, precomputed report table, or LLM calculation path yet.
- **Alternatives:** Load orders into Java and aggregate in memory; query mutable product fields; precompute/materialize every dashboard metric; allow generated SQL.
- **Why:** Small-business data fits indexed relational scans, database aggregation preserves decimal precision and reduces transfer, and stable methods such as `getRevenueSummary`, `getRevenueTrend`, and `getInventoryRisk` become safe Phase 5 tools. Immutable `paid_at` and line snapshots prevent later catalog edits from rewriting sales history.
- **Trade-offs:** Separate endpoint requests can observe different committed snapshots if commerce changes between them; repeated large-range queries are not cached; V3 backfills cannot recover already-lost pre-migration category/cost history. These are documented and measurable before adding complexity.
- **Concurrency:** Analytics is read-only at PostgreSQL `READ COMMITTED`. Commerce transactions atomically commit payment state, payment time, order facts, and inventory consumption, so readers never observe a half-paid order. No analytics lock blocks order processing.
- **Indexing:** Add `(store_id, status, paid_at)` for authorized range scans and retain order-item join keys. Further indexes/materialized views require `EXPLAIN` or latency evidence.

## ADR-014 — Models interpret intent; deterministic services own facts

- **Decision:** Put mock and OpenAI implementations behind one provider gateway, expose only eight typed read-only Spring AI tools, and deterministically construct the public response from collected tool results. Persist conversations and safe tool audits in PostgreSQL.
- **Alternatives:** Direct model SQL, model-generated response JSON treated as authoritative, provider-specific code in controllers, or a required paid provider in CI.
- **Why:** The model is useful for language-to-intent routing but is not a financial calculator or authorization boundary. A stable provider interface keeps local/CI behavior reproducible, while server assembly guarantees that displayed money, counts, charts, filters, and evidence agree with the Phase 4 engine.
- **Security:** Tenant identity is injected through trusted server `ToolContext`, never model-visible arguments. Tools have allow-listed parameters, period/result bounds, call budgets, timeouts, and no mutation capability. Product names, prior messages, and provider output remain untrusted data.
- **Failure isolation:** AI provider failures map to a safe 503 and do not change the availability of commerce or direct analytics. Failed tool calls retain audit metadata in an independent transaction.
- **Concurrency:** Conversation rows use optimistic versions. Concurrent turns may receive a retryable 409 instead of silently losing history; the analytics queries themselves remain read-only at `READ COMMITTED`.
- **Trade-offs:** The deterministic mock understands a documented set of intents rather than arbitrary language, and deterministic summaries are less stylistically flexible. Opt-in OpenAI mode requires a compatible Chat Completions model and live credentials; probabilistic quality is not claimed by offline evaluation.
- **Future:** Phase 6 can add evidence-backed interpretation/reporting behind the same boundary. It must not weaken numeric provenance, tool authorization, or the prohibition on direct mutation.

## ADR-015 — One explicit provider retry owner and evidence-typed analysis

- **Decision:** Disable OpenAI SDK retries, enforce transport timeouts, and let the provider gateway perform at most two transient attempts with bounded exponential backoff. Persist privacy-minimized request telemetry independently. Represent future analysis with separate evidence, findings, hypotheses, recommendations, and limitations.
- **Alternatives:** Stack SDK and framework retries; retry every exception; log raw requests/responses; store Phase 6 output as untyped prose or add normalized report tables before usage is known.
- **Why:** One retry owner makes worst-case latency/request cost calculable and prevents multiplicative attempts. Read-only tools make an attempt safe to repeat. Typed epistemic layers prevent a plausible causal story from being serialized as a verified fact, while a versioned document leaves persistence extensible.
- **Trade-offs:** The transient classifier and provider error taxonomy require maintenance as SDK exceptions change. Request telemetry is an estimate and not a billing ledger. JSONB report storage sacrifices ad hoc relational queries until a measured need justifies projection tables.
- **Boundary:** Phase 5.5 implements resilience, telemetry, contracts, and validation only. Phase 6 analysis generation, report APIs/tables, retention jobs, and recommendation behavior require separate implementation review.

## ADR-016 — Store-scoped, versioned Phase 6 report persistence

- **Decision:** Future Phase 6 reports use PostgreSQL relational metadata plus a validated, schema-versioned JSONB body. ADMIN access is store-wide; STAFF access is creator-only; all decisions derive from authenticated server context and all lookups include store scope.
- **Retrieval and indexing:** Project ownership, status, period, freshness, report/schema version, model metadata, creation time, and retention time into columns. Index ADMIN and STAFF list paths separately and add status/JSONB indexes only when an implemented query demonstrates the need.
- **Retention:** Configure 90 days as the initial development default and calculate `retention_until`, but do not enable deletion. User/store deletion, audit preservation, backups, legal holds, and compliance requirements need an approved production policy first.
- **Evaluation:** Deterministic mock evaluations are mandatory CI evidence. Live-provider behavior remains unverified and live evaluation stays opt-in and budget-capped; no billable run occurs without explicit approval.
- **Why:** Relational columns make tenant authorization and lifecycle retrieval inspectable and indexable, while JSONB preserves the evidence document as one versioned snapshot. Explicit role predicates prevent UI-only authorization, and delayed deletion avoids turning an engineering default into policy.
- **Trade-offs:** JSONB limits ad hoc relational analysis across reports, duplicated metadata requires consistency validation, and creator-only STAFF access needs a dedicated composite index. These costs are preferable to premature normalization or under-specified retention behavior.
- **Implementation:** Flyway V6, optimistic lifecycle transitions, creator-scoped idempotency, store/role-scoped repositories, and validated JSONB now implement this decision. Automatic retention deletion remains intentionally absent.

## ADR-017 — Server-owned business facts with allow-listed model synthesis

- **Decision:** Phase 6 computes revenue, contribution, price-volume, inventory, and scenario values deterministically. The model sees only bounded aggregate signals and may select allow-listed hypothesis/recommendation codes; the server owns wording templates, evidence links, priorities, assumptions, limitations, metrics, and charts.
- **Alternatives:** Let the model generate the complete report JSON; expose arbitrary analytics SQL; omit the model and hard-code every narrative; introduce a separate agent service.
- **Why:** This preserves numeric reproducibility and authorization while using a model only where linguistic interpretation can add value. Invalid structured output fails closed instead of contaminating a report.
- **Method:** Continuing-product price/volume effects use symmetric midpoint decomposition; new/discontinued products become lifecycle effects. Current inventory/selected-period velocity supports descriptive coverage only. Scenarios hold explicit variables constant and are not forecasts.
- **Consistency:** The multi-query analysis engine uses one read-only PostgreSQL `REPEATABLE READ` snapshot. MVCC yields internally consistent evidence without locking commerce writers.
- **Recommendations:** Priority follows deterministic revenue-change and current-risk rules. Expected financial impact remains unavailable unless calculable; every recommendation is advisory and requires later human approval.
- **Trade-offs:** The code allow-list constrains narrative variety and requires deliberate expansion. Synchronous generation is simple but may need a queue after measured load. Real-provider adherence remains unverified until an approved live run.
- **Boundary:** Phase 7 mutation proposals, approval transitions, freshness revalidation, execution, and operational audit are not authorized by this decision.

## ADR-018 — Restock execution is a separate, versioned human command

- **Decision:** A Phase 6 recommendation may seed a relational restock proposal, but it cannot mutate inventory. Proposal, ADMIN approval, and ADMIN execution are separate version-checked state transitions. Execution requires an explicit received-stock confirmation, reruns store/role/evidence/inventory freshness checks, and invokes a deterministic expected-version inventory command with an idempotency key.
- **Transaction boundary:** The inventory increment, inventory audit, proposal completion, proposal event, successful execution attempt, and outbox event commit in one PostgreSQL transaction. A unique proposal command ID makes replay return the existing result. Provider calls and external supplier calls are prohibited inside the transaction.
- **Authorization:** STAFF may propose only from completed reports they own and may read their own proposals. ADMIN may access, decide, and execute all proposals in the authenticated store. Every lookup includes server-derived store scope; cross-store IDs return not found.
- **Freshness:** Approval records the exact proposal version. Execution compares the active product, source evidence, report freshness, inventory version/time/values, and deterministic risk state with that approved snapshot. A mismatch produces terminal `STALE`; the system never silently rewrites and re-approves a quantity.
- **Recovery:** `EXECUTING` uses a bounded lease. Recovery checks the unique inventory result before returning an expired claim to `APPROVED` or finalizing it as `EXECUTED`. Only classified transient database failures receive a small bounded retry; business, authorization, and stale-data conflicts do not.
- **Semantics:** The initial operation represents confirmation of inventory physically received. It is not a supplier purchase order. Supplier ordering requires a separate supplier/PO/receipt domain before implementation.
- **Why:** Consequential actions need an inspectable human decision, protection from stale AI evidence and concurrent stock changes, exactly-once business effect under retries, and an append-only audit trail. PostgreSQL already provides the required transaction and coordination boundary.
- **Trade-offs:** The workflow adds tables, states, recovery logic, and explicit user steps. It intentionally sacrifices autonomous convenience and does not estimate demand or optimal order quantity.
- **Status:** Implemented in Flyway V8/V9 and the `operations` feature package. V9 adds proposal-generation usage, estimated-cost, and latency telemetry without changing the already-applied V8 workflow migration. The initial executable operation records physically received stock only; it does not create supplier orders or payments.

## ADR-019 — Local hardening first; measured complexity only

- **Decision:** Add correlation IDs, safe request metrics/logs, bounded
  in-process login throttling, HTTP size limits, explicit integration-test
  bounds, frontend bundle budgets, and CI security gates inside the existing
  modular monolith. Do not add a broker, Redis, tracing backend, metrics stack,
  WAF, or new service without a measured deployment requirement.
- **Performance:** Treat the reproducible k6/Docker results as local smoke
  evidence rather than an SLO. Require `EXPLAIN`/latency/saturation evidence
  before adding indexes, caches, or materialized views.
- **Outbox:** Durable transactional rows are useful now, but external delivery
  is not claimed. A relay begins only when a named consumer exists and must use
  bounded claims, acknowledgement, backoff, and consumer deduplication for
  at-least-once delivery.
- **Security:** Process-local login throttling limits opportunistic attempts but
  is not the horizontal-scale boundary. Edge/shared enforcement, secure cookies,
  TLS/security headers, centralized sessions, and secret rotation belong to an
  approved production deployment design.
- **Observability:** Emit route/method/status/duration and a request ID. Exclude
  request bodies, query values, cookies, credentials, prompts, and business
  rows. Keep Actuator exposure limited to health/info until an operator-owned
  metrics system exists.
- **Why:** These controls materially improve failure diagnosis and regression
  protection while keeping local development and CI deterministic, inexpensive,
  and understandable.
- **Trade-offs:** In-process controls reset on restart and are not shared;
  local metrics are ephemeral; production SLOs and multi-instance behavior
  remain future deployment decisions.

## ADR-020 — Same-origin managed AWS demo with an explicit spend gate

- **Decision:** Prepare a single-region S3/CloudFront frontend, HTTPS ALB, one
  ECS/Fargate backend task, and encrypted private Single-AZ RDS PostgreSQL using
  Terraform. Keep mock AI and disabled public registration as production
  defaults. Stage A may validate code locally but may not call AWS or expose a
  public endpoint; Stage B requires explicit approval of cost and exposure.
- **Alternatives:** A $42–50/month Lightsail host plus managed database reduces
  cost but increases host patch/release work and weakens network/IAM controls. A
  $12–16/month single host co-locates the app and database in one failure domain.
  Kubernetes, NAT Gateway, Redis, broker, WAF, and Multi-AZ RDS are not justified
  for expected demo traffic.
- **Networking:** CloudFront is the only browser origin. S3 stays private behind
  OAC. The ALB permits only the CloudFront managed prefix list and a generated
  origin header. The backend uses a public IP for outbound access without NAT
  but has no direct ingress. RDS is private and accepts PostgreSQL only from the
  backend security group.
- **Delivery:** GitHub Actions first runs the deterministic full gate, then a
  manual protected-environment job assumes a repository/environment-scoped AWS
  role through OIDC. Releases are immutable full-SHA images and cache-controlled
  S3 objects; ECS circuit-breaker health protects rollout. No long-lived AWS key
  belongs in GitHub.
- **Data and AI:** A controlled bootstrap briefly enables registration, then
  immediately disables it. The checked-in seed contains synthetic `DEMO-*`
  rows only. The public demo uses the deterministic mock; live-model use remains
  separately billable, capped, and unverified.
- **Availability and scaling:** One task and Single-AZ RDS keep the managed
  estimate near $88–95/month but are not highly available. Horizontal scaling
  requires shared sessions and distributed/edge login throttling first. Multi-AZ
  and additional tasks require a measured availability goal and new cost review.
- **Operations:** CloudWatch alarms cover target health, 5xx, p95, database CPU,
  connections/storage, provider/report/receipt failures, and recovery signals;
  AWS Budgets warn at 50/80/100%. Token use and estimated—not billed—AI cost are
  retained in PostgreSQL and safe logs. The outbox has no delivery alarm because
  it has no relay or consumer.
- **Trade-offs:** ALB, public IPv4, Fargate, and RDS accrue cost at little traffic;
  RDS restoration and real cloud rollout remain unverified until Stage B. The
  backend public IP is cost-driven outbound plumbing rather than public reach.
