# Phase 6 Review and Phase 7 Readiness

## Phase 7 resolution note

The two P1 gaps identified by this preflight were closed before Phase 7 feature
work. Flyway V7 adds generation ownership and bounded leases; a scheduled,
batch-limited compare-and-set reconciler marks expired `PENDING`/`RUNNING`
reports failed, and stale workers cannot complete them. Retrieval now compares
the validated JSONB document with authoritative relational identity, ownership,
status, type, versions, periods, timezone, and timestamps and fails closed with
`BUSINESS_REPORT_CORRUPT`. PostgreSQL tests cover crash/timeout recovery,
concurrent recovery, stale completion, and ownership-document tampering.

Review date: 2026-10-22. This is a code-and-test review of commit
`cb6183f6e69090e5aca12e82412502817995f65c`; it does not start Phase 7.

## Repository baseline

- Repository root: `/Users/humastetose-/Desktop/stockflow-ai`.
- Branch: `main`.
- `HEAD`, `origin/main`, and `origin/HEAD` all resolve to
  `cb6183f6e69090e5aca12e82412502817995f65c` (`feat: implement StockFlow AI through Phase 6`).
- The worktree was clean before this documentation review.
- Remote: `origin` at `https://github.com/dzshirley/stockflow-ai.git` for fetch and push.

This supersedes the earlier historical observation that the repository had no
`HEAD`; that observation is no longer current.

## Phase 6 acceptance review

| Area | Review result | Evidence |
|---|---|---|
| Report persistence | Verified | Flyway V6 creates relational ownership/status/period/model columns plus JSONB; PostgreSQL integration verifies JSONB, retention marker, lifecycle, and idempotent replay. |
| Report authorization | Verified | API scope comes from `StoreContext`; ADMIN is store-wide, STAFF is creator-only, cross-store/same-store unauthorized detail reads return not found. |
| Evidence-backed recommendations | Verified | Server-owned assembler builds evidence IDs, findings, hypotheses, and advisory recommendations; validator rejects dangling references and recommendations without evidence or required approval. |
| Deterministic calculations | Verified | Paid-order snapshots, decimal arithmetic, half-open store-local periods, contribution residuals, symmetric price-volume decomposition, zero-baseline semantics, and bounded scenario arithmetic have unit/integration coverage. |
| Provider failure isolation | Verified | Provider failure leaves a `FAILED` report with no JSONB document; separate outage coverage proves product/inventory/order state remains unchanged. |
| Existing AI tool authorization | Verified | Tool schemas contain no store/user argument; trusted `ToolContext` supplies `StoreContext`, inputs are bounded, and integration coverage verifies tenant isolation and sanitized audit metadata. |
| Live-provider behavior | Unverified by design | The billable test remains opt-in and was skipped. No claim is made about real-model code selection, latency, or cost. |
| Phase 7 mutations | Absent as required | No proposal, approval, restock execution, or AI mutation endpoint exists. |

No critical regression was found in normal completed-report authorization,
evidence construction, provider isolation, tool authorization, or analytics
arithmetic. Two reliability/security-hardening gaps must be addressed before an
operational Phase 7 rollout.

## Readiness gaps

### P1 — interrupted reports can remain pending or running indefinitely

`BusinessReportService.create` persists `PENDING`, then changes it to `RUNNING`
in a separate transaction. A normal in-process exception is caught and persisted
as `FAILED`, but process termination between those transactions or during model
work bypasses that catch. There is no lease, stale-state timeout, startup
reconciler, or administrative recovery command. Reusing the same idempotency key
returns the existing stranded row and does not resume or fail it.

Required readiness fix:

- define a configurable generation deadline and terminal recovery code;
- reconcile stale `PENDING`/`RUNNING` rows to `FAILED` using a bounded scheduled
  job or startup recovery pass;
- make reconciliation a conditional status/version update so it cannot race a
  real completion;
- keep retry as a new immutable report/idempotency command rather than silently
  overwriting the stranded report;
- add process-interruption/stale-row integration tests and age/status metrics.

This does not make completed reports incorrect, but it can leave users with a
permanent nonterminal record and should be fixed before Phase 7 adds another
stateful workflow.

### P1 — JSONB identity is validated internally but not compared with relational metadata

Retrieval scopes the relational row correctly and validates the deserialized
report document. It does not compare the JSONB `reportId`, `storeId`, creator,
status, period, or versions with the already-authorized relational columns. The
normal application write path creates both from one server object, so this is
not reachable through the current public API. It is nevertheless a defense-in-
depth gap: database corruption or an unsafe manual repair could return a valid
but mismatched report body from an authorized row.

Required readiness fix:

- add one relational/document consistency validator on report detail reads;
- fail closed with `BUSINESS_REPORT_CORRUPT` on any identity, ownership, status,
  version, or period mismatch;
- add a PostgreSQL integration test that swaps or alters JSONB metadata and
  proves no body is returned.

## Verified test results

- Backend: Java 21 `./mvnw verify` passed. Surefire ran 159 tests with zero
  failures/errors. Failsafe ran 15 tests: 14 passed and the one billable live
  provider test was skipped. Total non-billable result: 173 passed, 1 skipped.
- Frontend: frozen pnpm install completed; TypeScript passed; ESLint passed with
  zero warnings; Vitest passed 37 tests in 15 files; Vite production build
  passed. Vite emitted only the existing large-chunk advisory.
- Real-stack E2E: `docker compose up -d --build --wait` rebuilt a healthy
  PostgreSQL/backend/frontend stack, then Playwright Chromium passed the single
  protected registration-to-commerce/analytics/AI/Business Insights workflow
  in 4.2 seconds (5.0 seconds total).

The host default Java is 8, so backend verification used the repository's
available temporary Java 21 runtime. The pre-existing frontend `node_modules`
contained Linux native packages; it was preserved under `/private/tmp` and
recreated for macOS from the lockfile before verification. Neither environment
issue was a product test failure.

## Readiness conclusion

Phase 6 is functionally accepted for deterministic, read-only Business Insights
under the tested mock-provider boundary. Phase 7 architecture is ready for
implementation review, but operational implementation should begin by closing
the two P1 gaps above and adding the proposal schema/state-transition tests.
Real-provider behavior remains unverified and is not a Phase 7 execution
dependency: no provider call belongs in approval or execution transactions.
