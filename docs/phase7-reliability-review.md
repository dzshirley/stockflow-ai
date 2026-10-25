# Phase 7 Reliability Review

Status: completed on 2026-10-23. This review hardens and measures Phase 7; it
does not start Phase 8 or add forecasting, causal inference, or autonomous
operations.

## Stale-inventory test stall

One earlier full-suite report recorded
`changedInventoryMarksApprovedProposalStaleWithoutAddingStock` at about 897
seconds. That report contained elapsed time only: it did not preserve PostgreSQL
wait state, blockers, JVM stacks, container logs, or host resource telemetry.
Consequently, the root cause cannot be established from that run.

The review found no reproducible deadlock and no inverted lock order:

- proposal approval, execution, and recovery lock the proposal row before
  reading or conditionally updating inventory;
- the generic inventory adjustment used by the stale test updates inventory but
  never waits for a proposal row;
- Maven/JUnit has no repository setting that enables parallel test classes;
- the test datasource uses HikariCP's ten-connection maximum and 30-second
  connection acquisition timeout;
- PostgreSQL's default `lock_timeout` was unbounded, but its deadlock detector
  would ordinarily abort a true lock cycle rather than wait 15 minutes; and
- each Testcontainers class owns a static container which JUnit closes after the
  class. No leaked test process or container was present after controlled runs.

Three clean, controlled executions of `RestockProposalWorkflowIT` passed. Each
class completed in about 16 seconds and the stale-inventory case completed in
about 0.45 seconds. The subsequent full Java 21 gate also passed, with the
affected case taking 0.402 seconds. PostgreSQL startup was about 1.0–1.3 seconds
in the controlled runs. The anomaly is therefore not a deterministic failure in
the tested workflow. A transient
one-way lock wait, Docker Desktop/host scheduling pause, or resource starvation
remains possible, but none is proven.

The affected integration class now fails rather than hanging indefinitely:

- a 30-second JUnit timeout bounds every test and setup;
- Testcontainers startup is bounded at 30 seconds;
- PostgreSQL test settings use a 5-second lock timeout, 30-second statement
  timeout, one-second deadlock detection, and lock-wait logging;
- concurrent futures are bounded at ten seconds; and
- after ten seconds a diagnostic task logs `pg_stat_activity`, wait events,
  `pg_blocking_pids`, relevant JVM thread stacks, and safe identifiers. A
  separate JDBC connection with three-second connect/socket limits prevents the
  diagnostic itself from becoming an unbounded wait.

These are test diagnostics, not an approved production timeout policy. If the
problem recurs, preserve Surefire XML, PostgreSQL/Testcontainers logs, and the
diagnostic lines before rerunning. Production lock and statement timeouts should
be selected from measured transaction latency and failure budgets.

## Transactional outbox delivery

The application currently persists these events in the same transaction as the
associated state change:

- `ORDER_CREATED`, `ORDER_PAID`, `ORDER_PAYMENT_FAILED`, `ORDER_CANCELLED`, and
  `ORDER_EXPIRED`;
- `RESTOCK_RECEIPT_RECORDED`.

There is no relay and no consumer reads `outbox_events`. Current guarantees are
therefore atomic durable event recording and rollback with the business change,
not external delivery. `published_at` remains null unless a future publisher
updates it. No current commerce, analytics, AI, or operations behavior depends
on delivery, so a broker or relay would add operations without serving a
consumer and is not justified now.

When an actual consumer is introduced, use a small scheduled relay in the
modular monolith first: claim bounded rows ordered by creation time with
`FOR UPDATE SKIP LOCKED`, publish the immutable event ID, and set
`published_at` only after acknowledgement. Add attempt count, next-attempt time,
last safe error, bounded exponential backoff, and an operator-visible terminal
state. A crash after publish and before marking produces duplicates, so this is
at-least-once delivery and every consumer must deduplicate by event ID (or the
operation command ID where appropriate). A broker choice should follow the
real consumer's durability and throughput requirements.

## Frontend bundle

The measured baseline Vite production build had one 777.47 kB minified,
225.15 kB gzip JavaScript entry chunk. Source-map inventory identified Recharts
as the largest dependency family (about 831 KiB of source content), followed by
React DOM (620 KiB), React Router (367 KiB), and TanStack Query core (147 KiB).
All pages, including chart-heavy analytics pages, were eagerly imported.

Routes now use `React.lazy` and `Suspense`. The post-change initial entry is
257.19 kB minified and 79.55 kB gzip: reductions of 66.9% and 64.7%
respectively. Recharts is loaded in route/chart chunks; the largest on-demand
chart chunk is 357.55 kB minified (103.52 kB gzip). Vite no longer emits the
500 kB chunk warning. This keeps the optimization at the route boundary and
does not change feature ownership or introduce manual vendor chunking.

## Live-provider readiness

The billable suite remains opt-in through `RUN_LIVE_AI_EVAL=true` and was not
executed in this review. It runs three top-level tasks, permits at most four tool
calls per task and 15 provider turns total, caps output at 300 tokens per turn,
and has a hard estimated-cost ceiling of USD 0.05. A configured lower ceiling is
allowed; a zero, negative, or higher value fails before a provider request.

The suite checks real tool selection and persisted normalized arguments,
compares revenue with the deterministic analytics service, covers a multi-tool
request and follow-up, and records sanitized model, latency, provider token
usage, tool count, estimated cost, success, and safe failure data. Prompts,
credentials, and business rows are excluded. MockWebServer tests cover supported
transient HTTP failures, reset/timeout/interruption, malformed JSON, bounded
retry, and credential-safe errors. Unsupported requests are covered by the
mandatory deterministic fixture.

Real model behavior, provider-side billing, network/TLS edge cases, and an
actual provider's malformed-response behavior remain unverified until an
explicitly approved run. Estimated cost is not an invoice.

## Priorities before and during Phase 8

1. **P1 — preserve evidence on recurrence:** retain CI/Surefire, container, and
   wait-diagnostic artifacts for any slow or timed-out test. Do not weaken the
   bounds or label the incident a deadlock without blocker evidence.
2. **P1 — approved provider evidence:** run the bounded live suite only with an
   explicit owner, key, and budget before making real-model reliability claims.
3. **P2 — production database wait policy:** define lock/statement timeout and
   transaction-latency SLOs from staging measurements rather than copying test
   values.
4. **P2 — protect frontend gains:** add a lightweight initial-entry performance
   budget after the split has proved stable in CI.
5. **P3 — outbox relay:** implement only when a named consumer exists; require
   consumer idempotency and operational retry/dead-letter ownership first.

No finding blocks Phase 8 planning. Phase 8 should begin with repeatable
contention/fault tests and preserved diagnostics, then performance budgets and
an approved live-provider evidence run. A relay should remain conditional on a
real asynchronous use case.

## Verification record

- Backend Java 21 `./mvnw verify`: 163 fast tests and 24 non-billable
  integration tests passed (187 total); one live-provider test skipped; build
  succeeded in 1 minute 24 seconds.
- Frontend `pnpm check`: TypeScript, zero-warning ESLint, 41 Vitest tests in 17
  files, and the production build passed.
- Rebuilt Docker Compose frontend/backend: healthy startup with Flyway V9.
- Playwright Chromium: both commerce/AI/report and restock operations workflows
  passed (2/2) against the rebuilt stack.
- `git diff --check`: passed.

The billable live-provider evaluation was not authorized or executed.
