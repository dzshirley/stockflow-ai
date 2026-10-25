# Phase 8 Reliability and Concurrency Review

Reviewed on 2026-10-23.

## Transaction and concurrency boundaries

PostgreSQL remains the coordination boundary. Order creation acquires the
store/idempotency gate, locks/reserves products in deterministic UUID order,
and commits order, reservation, audit, idempotency result, and outbox together.
Terminal order transitions lock the order row. Restock execution locks the
proposal before validating and applying one conditional inventory update, with
a unique command ID protecting the inventory adjustment and execution attempt.

Phase 8 added PostgreSQL tests for concurrent use of one order idempotency key
and a payment-versus-cancellation race. The first produces one order and one
reservation; the second permits exactly one terminal outcome and verifies the
matching inventory and outbox effect. The stale inventory scenario is repeated
three times per suite run. Concurrent futures are bounded rather than waiting
indefinitely.

## Test-stall investigation

The earlier approximately 897-second stale-inventory observation still has no
preserved blocker/thread evidence, so its root cause cannot be established.
The Phase 8 focused run completed 11 commerce cases and 7 restock cases in
44.5 seconds; the stale test did not stall. The restock workflow now repeats
that case three times and retains 30-second JUnit/startup/statement bounds,
five-second lock bounds, and delayed PostgreSQL/JVM diagnostics.

A later full gate, while browser/build/load work also competed for the same
7.9-GB Docker VM, exposed a different anomaly: the report-recovery case took
201.134 seconds while passing. No lock/blocker capture was available for that
run, so a deadlock is again not claimed. Its test context had production
scheduling enabled while also invoking recovery manually. Phase 8 now disables
scheduled jobs in that integration class, adds a 30-second JUnit bound,
30-second container/statement bounds, a five-second database lock timeout, lock
wait logging, and ten-second future bounds. The controlled rerun and final gate
results are recorded in `phase8-verification.md`.

The evidence supports a test-isolation/resource-contention risk, not a proven
application database deadlock. A future recurrence must preserve
`pg_stat_activity`, `pg_blocking_pids`, PostgreSQL logs, JVM stacks, Surefire
XML, Testcontainers lifecycle logs, and host saturation data.

## Recovery and retries

- Provider transport retries only classified transient failures, with two total
  attempts and bounded exponential backoff. Malformed/unsupported responses do
  not loop. AI outages do not gate commerce or direct analytics.
- Report and restock recovery claim bounded batches with row locks and
  `SKIP LOCKED`. Repeating restock recovery after reconciliation returns zero.
- Order and restock commands do not automatically retry business mutations.
  Clients may safely replay only with the original idempotency/command key.
- Readiness uses `/actuator/health/readiness`; liveness and application health
  remain separately available. Compose waits on PostgreSQL and backend health.

## Transactional outbox

Persisted event types are `ORDER_CREATED`, `ORDER_PAID`,
`ORDER_PAYMENT_FAILED`, `ORDER_CANCELLED`, `ORDER_EXPIRED`, and
`RESTOCK_RECEIPT_RECORDED`. The current guarantee is atomic, durable recording
with the business transaction. There is no relay and no consumer; therefore
there is no external-delivery guarantee and no current feature depends on one.

No broker or relay was added. When a named consumer exists, the minimal design
is a bounded scheduled claimer using `FOR UPDATE SKIP LOCKED`, immutable event
IDs, acknowledgement before `published_at`, attempt/next-attempt/error fields,
bounded backoff, and an operator-visible terminal state. A crash between publish
and marking creates duplicates, so delivery is at least once and consumers must
deduplicate by event ID.

## Remaining reliability risks

1. The original 897-second incident and later 201-second report-test anomaly
   lack contemporaneous blocker evidence; bounds now turn recurrence into a
   diagnosable failure.
2. Production lock/statement timeouts and connection-pool sizing still require
   staging measurements; test values are not a production policy.
3. Local sessions and login throttling require a reviewed multi-instance design.
4. Outbox delivery is intentionally absent until an actual consumer is named.
5. Real-provider behavior remains unverified because no billable evaluation was
   authorized.
