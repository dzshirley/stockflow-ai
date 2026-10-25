# Verified Resume Bullets

Use the version that matches the role. “Prepared AWS deployment” is accurate;
“deployed to AWS” is not accurate until Stage B succeeds and is documented.

## General Software Engineer

- Built StockFlow AI, a Java 21/Spring Boot and React/TypeScript commerce
  operations platform with 44 REST endpoints spanning multi-tenant catalog,
  inventory, orders, analytics, evidence-backed AI reports, and human-approved
  restock workflows.
- Implemented PostgreSQL transaction, row-locking, optimistic-version, and
  idempotency controls that prevent overselling and duplicate order/receipt
  effects; verified concurrency, rollback, tenant isolation, and state machines
  with real PostgreSQL Testcontainers tests.
- Established 245-case automated coverage (244 executed/passed; one opt-in paid
  provider test skipped), including JUnit/MockMvc, Vitest, and three Playwright
  full-stack workflows; reduced initial minified JavaScript 66.9% from 777.47 kB
  to 257.19 kB with route splitting and enforced bundle budgets.
- Measured a reproducible local mixed API workload at 225.07 requests/second,
  0% errors, 4.32 ms p50, 26.74 ms p95, and 83.22 ms p99; documented dataset,
  hardware, query plan, utilization sample, and non-production limitations.

## Backend Software Engineer

- Designed a package-by-feature Spring Boot modular monolith backed by
  PostgreSQL/Flyway, keeping cross-domain order, inventory, audit, idempotency,
  and transactional-outbox writes atomic without distributed transactions.
- Implemented deterministic lock ordering, conditional inventory updates,
  store-scoped idempotency gates, terminal-state locking, command deduplication,
  and lease recovery; concurrent integration tests prove one last-item buyer,
  one same-key order, and one receipt increment.
- Enforced server-derived tenant/RBAC boundaries for ADMIN/STAFF report and
  proposal ownership, with CSRF, BCrypt sessions, bounded login failures,
  centralized errors, request correlation, readiness probes, and safe metrics/logs.
- Prepared a costed AWS design with private RDS, ECS/Fargate, CloudFront/S3,
  HTTPS ALB, immutable ECR images, OIDC/manual CD, health rollback, alarms,
  budgets, backup/restore, and teardown procedures; no cloud spend was initiated.

## AI Software Engineer

- Built a bounded AI analyst over eight typed read-only tools, keeping store
  identity server-injected and assembling every authoritative KPI/chart from
  deterministic analytics rather than model-authored financial arithmetic.
- Designed versioned evidence-backed reports that separate verified
  observations, calculations, hypotheses, recommendations, uncertainty, and
  limitations, with relational metadata plus validated PostgreSQL JSONB snapshots.
- Implemented human-in-the-loop restock proposals where AI selects only from
  bounded evidence candidates; ADMIN approval, inventory/evidence freshness,
  physical receipt, idempotency, and atomic audit/outbox writes remain server-owned.
- Added provider timeout/reset/429/5xx/malformed-response fault injection,
  bounded retry/backoff, token/latency/tool/cost telemetry, and 100 deterministic
  evaluation prompts; real-provider evaluation remains explicitly opt-in and
  unexecuted without an approved key/budget.

## Claims to avoid

- Do not claim production traffic, customer usage, revenue impact, a live AWS
  deployment, exactly-once messaging, model accuracy against a live provider,
  formal penetration testing, or high availability.
- The 30,000+ transaction and 200+ SKU figures describe historical business
  motivation, not records processed by this synthetic portfolio application.
