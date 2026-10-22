# Business Analysis Evaluation

## Method

The required CI baseline is deterministic and non-billable. `business-analysis-evaluation-cases.csv` contains 44 representative questions across revenue decline/growth, product/category contribution, average-order-value and profit investigation, price-volume decomposition, new/discontinued products, inventory risk/excess/low sales, scenarios, unsupported prediction, mutation, SQL, causal wording, and cross-store prompt injection.

`BusinessAnalysisPlannerEvaluationTest` verifies analysis-type routing and rejection. Calculation tests independently verify numeric changes, contribution reconciliation, product lifecycle handling, symmetric price-volume reconciliation, scenario arithmetic, unavailable profit, coverage, and input bounds. Validator and integration tests verify evidence references, epistemic separation, JSONB persistence, lifecycle, idempotency, numeric consistency, and tenant authorization. The mock provider returns only allow-listed synthesis codes and is the mandatory CI provider.

## Recorded Phase 6 result — 2026-10-22

- All 44/44 business-analysis routing cases passed.
- The complete backend gate passed 159 fast/unit/web/migration/evaluation/fault tests and 14 non-billable integration tests (173 passed); the one billable live-provider integration was skipped by default.
- Frontend strict TypeScript, zero-warning ESLint, 37 Vitest tests across 15 files, and the production build passed.
- The production Compose stack built and all three services became healthy. Direct and frontend-proxied health returned `UP`, and PostgreSQL reported successful Flyway version 6.
- Playwright Chromium passed the single 4.3-second end-to-end workflow (6.4 seconds total): protected redirect, registration, commerce, paid analytics, AI follow-up, Business Insights generation/persistence/rendering, evidence-backed decomposition, and logout.

No live-provider evaluation was executed. Real model routing, structured-output adherence, latency, and token cost therefore remain unverified.

## Scoring interpretation

- **Numeric accuracy:** exact deterministic assertions using decimal comparisons.
- **Evidence validity:** completed documents pass `BusinessAnalysisValidator`; invented or dangling references fail.
- **Tool/analysis selection:** correct type or safe unsupported result for all dataset rows.
- **Causal-claim control:** only qualified hypothesis codes are accepted; the server templates causal wording as untested and adds limitations.
- **Schema validity:** JSONB round-trip and version fields are checked against PostgreSQL.
- **Authorization:** ADMIN store-wide, STAFF creator-only, anonymous denial, and cross-store not-found behavior.
- **Task completion:** a report is either validated `COMPLETED` or explicit `FAILED`; there is no partial completed document.
- **Latency/cost:** Micrometer and report metadata capture model latency and provider usage when supplied. The deterministic mock reports usage as unavailable.

## Live-provider boundary

The Phase 5.5 opt-in live suite remains disabled unless `RUN_LIVE_AI_EVAL=true` and a user explicitly approves billable execution. Phase 6 does not weaken its request, token, or estimated-cost caps. Offline success must not be interpreted as proof that a real provider always selects valid codes or follows the prompt.
