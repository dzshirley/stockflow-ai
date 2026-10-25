# StockFlow AI Engineering Metrics

Verified: 2026-10-24

## Executive summary

- **44 application REST endpoints** are implemented as distinct HTTP
  method-and-route combinations across 10 Spring `@RestController` classes.
- **245 unique automated test cases are implemented/discovered** across the
  backend, Vitest, and Playwright suites.
- **244 test bodies executed and passed**, 0 failed, and 1 was skipped. The
  skipped test is the explicitly opt-in, billable live-provider evaluation.

The 27 PostgreSQL integration tests are a subset of the 201 backend JUnit test
cases. They are reported separately below but are not added to the grand total a
second time.

## Counting method and scope

Endpoint counts were derived from every `@RestController`, its class-level
`@RequestMapping`, and each method-level `@GetMapping`, `@PostMapping`,
`@PatchMapping`, or `@DeleteMapping` under `backend/src/main/java`. One endpoint
means one unique HTTP method plus resolved route. `@RestControllerAdvice` is not
an endpoint, and query-parameter variations do not create additional routes.

The count intentionally covers repository-owned application controllers. It
does not count Spring Boot Actuator's framework-generated management endpoints
or the security filter's generic `OPTIONS /**` preflight allowance because
neither is an implemented application controller method.

Backend implemented counts use the cases discovered in clean Maven
Surefire/Failsafe XML reports, so parameterized and dynamic tests are counted by
their expanded cases rather than by Java annotation count. Frontend counts use
fresh Vitest and Playwright discovery output and were cross-checked against the
test source files.

## REST API endpoint inventory

| Controller | HTTP method | Route |
|---|---|---|
| `AiController` | POST | `/api/ai/query` |
| `AiController` | GET | `/api/ai/conversations` |
| `AiController` | GET | `/api/ai/conversations/{conversationId}` |
| `BusinessAnalysisController` | POST | `/api/business-analysis/reports` |
| `BusinessAnalysisController` | GET | `/api/business-analysis/reports` |
| `BusinessAnalysisController` | GET | `/api/business-analysis/reports/{reportId}` |
| `AnalyticsController` | GET | `/api/analytics/overview` |
| `AnalyticsController` | GET | `/api/analytics/orders/summary` |
| `AnalyticsController` | GET | `/api/analytics/revenue/trend` |
| `AnalyticsController` | GET | `/api/analytics/products/top` |
| `AnalyticsController` | GET | `/api/analytics/categories/performance` |
| `AnalyticsController` | GET | `/api/analytics/inventory/summary` |
| `AnalyticsController` | GET | `/api/analytics/inventory/risk` |
| `AuthController` | GET | `/api/auth/csrf` |
| `AuthController` | POST | `/api/auth/register` |
| `AuthController` | POST | `/api/auth/login` |
| `AuthController` | POST | `/api/auth/logout` |
| `AuthController` | GET | `/api/auth/me` |
| `HealthController` | GET | `/api/health` |
| `InventoryController` | GET | `/api/inventory` |
| `InventoryController` | GET | `/api/inventory/{productId}` |
| `InventoryController` | POST | `/api/inventory/{productId}/adjustments` |
| `InventoryController` | GET | `/api/inventory/{productId}/adjustments` |
| `RestockProposalController` | POST | `/api/restock-proposals/generate` |
| `RestockProposalController` | GET | `/api/restock-proposals` |
| `RestockProposalController` | GET | `/api/restock-proposals/{id}` |
| `RestockProposalController` | POST | `/api/restock-proposals/{id}/approve` |
| `RestockProposalController` | POST | `/api/restock-proposals/{id}/reject` |
| `RestockProposalController` | POST | `/api/restock-proposals/{id}/cancel` |
| `RestockProposalController` | POST | `/api/restock-proposals/{id}/execute` |
| `RestockProposalController` | GET | `/api/restock-proposals/{id}/history` |
| `OrderController` | POST | `/api/orders` |
| `OrderController` | GET | `/api/orders/{id}` |
| `OrderController` | GET | `/api/orders` |
| `OrderController` | POST | `/api/orders/{id}/payment` |
| `OrderController` | POST | `/api/orders/{id}/cancel` |
| `ProductController` | POST | `/api/products` |
| `ProductController` | GET | `/api/products/{id}` |
| `ProductController` | GET | `/api/products` |
| `ProductController` | PATCH | `/api/products/{id}` |
| `ProductController` | DELETE | `/api/products/{id}` |
| `StoreController` | GET | `/api/store` |
| `StoreController` | GET | `/api/store/members` |
| `StoreController` | POST | `/api/store/members` |

### Endpoint totals by controller

| Controller | GET | POST | PATCH | DELETE | Total |
|---|---:|---:|---:|---:|---:|
| `AiController` | 2 | 1 | 0 | 0 | 3 |
| `BusinessAnalysisController` | 2 | 1 | 0 | 0 | 3 |
| `AnalyticsController` | 7 | 0 | 0 | 0 | 7 |
| `AuthController` | 2 | 3 | 0 | 0 | 5 |
| `HealthController` | 1 | 0 | 0 | 0 | 1 |
| `InventoryController` | 3 | 1 | 0 | 0 | 4 |
| `RestockProposalController` | 3 | 5 | 0 | 0 | 8 |
| `OrderController` | 2 | 3 | 0 | 0 | 5 |
| `ProductController` | 2 | 1 | 1 | 1 | 5 |
| `StoreController` | 2 | 1 | 0 | 0 | 3 |
| **Total** | **26** | **16** | **1** | **1** | **44** |

### Endpoint totals by domain module

| Domain module | Endpoints |
|---|---:|
| Authentication | 5 |
| Store membership | 3 |
| Product catalog | 5 |
| Inventory | 4 |
| Orders and payments | 5 |
| Deterministic analytics | 7 |
| AI Data Analyst | 3 |
| AI Business Analyst | 3 |
| Restock operations | 8 |
| Application health | 1 |
| **Total** | **44** |

## Implemented test inventory

| Suite/category | Test files/classes | Implemented/discovered cases | Relationship |
|---|---:|---:|---|
| Backend JUnit — all Surefire and Failsafe | 33 classes | 201 | Unique backend total |
| Maven Surefire JUnit | 27 classes | 172 | Subset of backend total |
| Maven Failsafe integration JUnit | 6 classes | 29 | Subset of backend total |
| PostgreSQL Testcontainers integration | 4 classes | 27 | Subset of Failsafe and backend totals |
| Other backend integration | 2 classes | 2 | H2 outage isolation plus opt-in live evaluation |
| Frontend Vitest | 17 files | 41 | Separate frontend suite |
| Playwright E2E | 1 file | 3 | Separate browser suite |
| **Unique total** | **51 files/classes** | **245** | Backend counted once |

The requested top-level test categories are therefore 172 backend unit/API
cases, 29 backend integration cases, 41 frontend Vitest cases, and three
Playwright E2E cases. The integration total includes the one optional live case
that is discovered but skipped by default.

The deterministic AI evaluation fixtures contain 100 question cases: 56 for
the AI Data Analyst and 44 for the AI Business Analyst. All 100 executed and
passed inside the Surefire total; they are subsets, not additional tests. The
AI Data Analyst evaluation class also contributes one fixture breadth check and
one conversation follow-up case, which explains its 58 runner-discovered tests.
One optional live-provider integration test was discovered and skipped. It was
not blocked or executed, and no provider charge was incurred.

The PostgreSQL subset consists of:

| Integration class | Cases |
|---|---:|
| `BusinessAnalysisWorkflowIT` | 6 |
| `BusinessAnalysisProviderFailureIT` | 1 |
| `CommerceWorkflowIT` | 11 |
| `RestockProposalWorkflowIT` | 9 |
| **Total** | **27** |

## Fresh execution results

| Suite/category | Implemented/discovered | Actually executed | Passed | Failed | Skipped |
|---|---:|---:|---:|---:|---:|
| Maven Surefire JUnit | 172 | 172 | 172 | 0 | 0 |
| PostgreSQL Testcontainers integration | 27 | 27 | 27 | 0 | 0 |
| Other backend integration | 2 | 1 | 1 | 0 | 1 |
| **Backend JUnit total** | **201** | **200** | **200** | **0** | **1** |
| Frontend Vitest | 41 | 41 | 41 | 0 | 0 |
| Playwright E2E | 3 | 3 | 3 | 0 | 0 |
| **Unique automated-test total** | **245** | **244** | **244** | **0** | **1** |

Blocked tests: **0**. The live evaluation is intentionally skipped by its
explicit opt-in guard, not blocked by a missing CI dependency.

“Actually executed” excludes skipped test bodies. Maven discovered 29 Failsafe
cases, but `LiveOpenAiEvaluationIT` was skipped by its default opt-in guard.
No paid provider request was made. The H2-backed `AiOutageIsolationIT` executed
and passed; it is not included in the PostgreSQL subtotal.

## Verification commands and evidence

The following suites were run from the current working tree across the final
Phase 9 Stage A verification session ending 2026-10-24:

```bash
cd backend
./mvnw clean verify

cd ../frontend
pnpm check
pnpm test:e2e
```

The backend command was executed in a Java 21 container because the host's
default Java is Java 8; Testcontainers used the local Docker daemon. The clean
run produced 26 Surefire XML reports and 6 Failsafe XML reports under
`backend/target`, eliminating stale reports from older focused executions.

Recorded runner results:

- Maven Surefire: 172 passed, 0 failed, 0 skipped.
- Maven Failsafe: 28 passed, 0 failed, 1 skipped, 29 discovered total.
- Combined backend: 200 passed, 0 failed, 1 skipped, 201 discovered total.
- Vitest: 17 files passed; 41 tests passed, 0 failed, 0 skipped.
- Playwright Chromium: 3 tests passed, 0 failed, 0 skipped.

## Measured performance

The local mixed-workload p99 confirmation used five virtual users for 15
seconds, an isolated synthetic tenant, and 100 paid seed orders against the
production-style Compose stack. It completed 4,316 HTTP requests at 225.07
requests/second with a 0.00% request error rate. Overall latency was 4.32 ms
p50, 26.74 ms p95, and 83.22 ms p99. All 4,110 checks passed.

| Endpoint family | p50 | p95 | p99 |
|---|---:|---:|---:|
| Authentication | 396.09 ms | 403.59 ms | 404.27 ms |
| Product listing | 3.58 ms | 18.27 ms | 105.25 ms |
| Inventory lookup | 3.26 ms | 17.97 ms | 46.38 ms |
| Analytics overview + trend | 4.35 ms | 18.79 ms | 43.23 ms |
| Order create + cancel | 11.05 ms | 50.76 ms | 122.33 ms |
| Business report retrieval | 4.57 ms | 23.63 ms | 72.43 ms |
| Proposal listing | 4.08 ms | 16.81 ms | 39.55 ms |
| Receipt confirmation | 28.06 ms | 28.06 ms | Not meaningful (one sample) |

A one-shot sample during this run observed the backend at 175.09% CPU and 518
MiB, PostgreSQL at 23.44% CPU and 69.71 MiB, Nginx at 8.31% CPU and 11.06 MiB,
and k6 at 11.71% CPU and 69.13 MiB. This is a point observation, not an average
or capacity limit. A prior comparable run completed 5,426 requests at 320.43
requests/second with 2.98 ms p50 and 12.17 ms p95, demonstrating local-run
variance rather than a guaranteed throughput.

The production frontend entry is 257.19 kB minified / 79.55 kB gzip, down from
the measured 777.47 kB / 225.15 kB gzip baseline. The largest lazy chunk is
357.56 kB / 103.52 kB gzip. An analyzed paid-sales query completed in 0.623 ms
with 45 shared-buffer hits and no disk reads on the 100-paid-order fixture.
Detailed conditions and limitations are in
[performance-report.md](performance-report.md).

## Verified portfolio summary

StockFlow AI implements 44 typed REST endpoints in a Java 21/Spring Boot
modular monolith with PostgreSQL transactions, tenant/role authorization,
idempotent order and receipt commands, evidence-backed AI reports, and explicit
human approval for inventory operations. Its current automated inventory is
245 discovered cases; 244 executed and passed in the final gate, with only the
billable live-provider case skipped. The React/TypeScript frontend has three
passing real-stack browser workflows, enforced bundle budgets, and a measured
66.9% reduction in initial minified JavaScript. These facts describe verified
local implementation evidence, not production traffic or deployment readiness.

## Verified totals

| Metric | Count |
|---|---:|
| Distinct application REST method + route endpoints | **44** |
| Implemented/discovered automated tests | **245** |
| Automated test bodies actually executed | **244** |
| Passed automated tests | **244** |
| Failed automated tests | **0** |
| Skipped automated tests | **1** |
