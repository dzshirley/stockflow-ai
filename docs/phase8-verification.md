# Phase 8 Verification Record

Verified across the final local session ending 2026-10-24. All results below
come from the current working tree. They are development evidence, not a
production certification, penetration test, capacity guarantee, or live-model
evaluation.

## Acceptance summary

- The application exposes 44 distinct application-owned REST method/route
  combinations. Phase 8 adds hardening around existing contracts rather than
  expanding the API surface.
- The backend discovered 200 JUnit cases: 171 Surefire cases and 29 Failsafe
  cases. Of these, 199 executed and passed; the explicitly opt-in billable live
  provider case was skipped.
- Frontend verification passed strict TypeScript, zero-warning ESLint, 41
  Vitest cases across 17 files, a production build, and both JavaScript bundle
  budgets.
- Three Chromium Playwright workflows passed against the rebuilt Compose stack,
  including the negative/idempotency/isolation workflow added in Phase 8.
- The p99-enabled mixed local k6 profile completed 4,316 requests with zero
  failures, 225.07 requests/second, 4.32 ms p50, 26.74 ms p95, and 83.22 ms
  p99. A prior comparable run recorded 5,426 requests at 320.43 requests/second.
- No paid live-provider evaluation or cloud provisioning was performed.

## Verification commands and results

| Check | Result |
|---|---|
| Java 21 `./mvnw clean verify` | 200 discovered; 199 passed, 0 failed, 1 skipped; 55.964 s Maven total |
| PostgreSQL Testcontainers subset | 27 passed across four workflow classes |
| `pnpm check` | TypeScript, ESLint, 41 Vitest cases, production build, and bundle budgets passed |
| `pnpm test:e2e` | 3/3 Chromium tests passed; 9.3 s total |
| Compose rebuild/readiness | PostgreSQL, backend, and frontend healthy; Flyway V9 validated |
| k6 Phase 8 API smoke | p99 run: 4,316 requests; 0 failures; 4,110/4,110 checks passed |
| `pnpm audit --prod --audit-level high` | No known production frontend vulnerabilities |
| Common credential-pattern scan | No matching committed/worktree credential pattern found |

The modified GitHub Actions workflow was not pushed or run on GitHub during
this local milestone. Its least-privilege permissions, time bounds, readiness
gate, deterministic test commands, E2E cleanup, secret scan, and frontend audit
were inspected; the underlying commands passed locally. Remote runner behavior,
cache integration, and action availability remain unverified until a branch is
pushed and CI runs.

The final Maven reports contain 26 Surefire test classes and six Failsafe test
classes. The PostgreSQL subset is `BusinessAnalysisWorkflowIT` (6),
`BusinessAnalysisProviderFailureIT` (1), `CommerceWorkflowIT` (11), and
`RestockProposalWorkflowIT` (9). The remaining two Failsafe cases are the H2
AI-outage isolation test and the skipped live-provider evaluation.

## Reliability and concurrency evidence

Order concurrency now covers concurrent replays of one idempotency key and a
payment-versus-cancellation race. The tests assert a single durable order effect
and exactly one terminal outcome. Restock stale-inventory validation runs three
times per full suite, uses bounded waits, and asserts that reconciliation is
idempotent.

The previously observed stale-test stall was not reproduced, and the available
historical evidence does not establish a database deadlock. A separate
report-recovery case ran for 201.134 seconds once while the Docker VM was under
concurrent build/browser/load activity. After disabling production scheduling
inside that test class and adding JUnit, future, database lock, statement, and
container startup bounds, the isolated class passed six cases in 18.78 seconds,
the recovery case took 1.02 seconds, and the final full gate did not reproduce
the anomaly. The evidence supports test isolation/resource contention as a
risk; it does not prove a single root cause.

Provider retries remain bounded to two total attempts for classified transient
transport failures. Malformed responses fail without retry. AI failures remain
isolated from product, inventory, order, and direct analytics workflows.

## Performance and frontend evidence

Route-level lazy loading reduced the production initial JavaScript entry from
777.47 kB minified / 225.15 kB gzip to 257.19 kB / 79.55 kB gzip. The largest
on-demand chunk is 357.56 kB / 103.52 kB gzip. The build now fails if the raw
initial entry exceeds 300 KiB or any JavaScript chunk exceeds 400 KiB.

The p99-enabled local k6 profile used five virtual users for 15 seconds against
an isolated tenant and a 100-order paid dataset. Endpoint-family p95 values
ranged from 16.81 ms for proposal reads to 50.76 ms for commerce writes; login
was 403.59 ms p95 because it includes BCrypt. Overall p99 was 83.22 ms. A
one-shot utilization sample observed 175.09% CPU/518 MiB for the backend and
23.44% CPU/69.71 MiB for PostgreSQL; it is not a sustained average. These are
local smoke measurements, not SLOs or capacity claims. See
[performance-report.md](performance-report.md) for setup, tagged measurements,
the faster prior run, and query-plan evidence.

## Security and operations evidence

Phase 8 adds bounded privacy-minimized login failure throttling, request-ID
propagation, safe completion logging, request latency metrics, one-megabyte body
limits, a 16-KiB header limit, expanded operations security tests, CI secret and
frontend dependency checks, and weekly dependency update configuration.

The throttle and HTTP sessions remain process-local. Production TLS, edge
security headers, shared multi-instance enforcement, backend/image software
composition analysis, backup policy, and formal penetration testing remain
deployment work. See [security-review.md](security-review.md).

## Corrected issues found during verification

The first Playwright attempt exposed `crypto.randomUUID` as unavailable on the
non-secure `host.docker.internal` browser origin and caused the Business
Insights workflow to time out. The UUID utility now uses `randomUUID` when
available and a correctly versioned Web Crypto `getRandomValues` fallback
otherwise. After rebuilding, all three workflows passed.

An intermediate k6 run reused a fixed receipt command UUID from a prior run and
correctly received one conflict. The workload now derives a unique receipt
command from its proposal ID; the final acceptance run had zero failures. Both
incidents are retained here so the final passing result is not mistaken for an
initial clean run.

## Remaining limitations

1. Real OpenAI model selection, structured output, latency, and usage remain
   unverified because no billable run was authorized.
2. The historical long-test observations lack contemporaneous blocker/thread
   evidence; new bounds and diagnostics make recurrence actionable.
3. The outbox provides atomic durable event recording only. No consumer exists,
   so no relay or external delivery guarantee was added.
4. Local load results do not cover production-shaped large tenants, WAN/TLS,
   multi-instance sessions, sustained soak, or infrastructure saturation.
5. Phase 9 deployment, cloud resources, and production policy decisions were
   deliberately not started.
6. The hardened GitHub Actions workflow still requires its first remote run;
   this does not block local correctness evidence but is required before merge.
