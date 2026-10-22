# Phase 5 AI Evaluation

## Purpose and scope

The default evaluation measures the deterministic language-to-tool contract without network access or model cost. It does not claim general natural-language accuracy and is not a substitute for production monitoring or an opt-in real-model study.

The source fixture is `backend/src/test/resources/ai-evaluation-cases.csv`. It contains 56 representative prompts: revenue summaries/comparisons/trends, product/category ranking, orders/AOV/units, inventory summary/risk, multi-tool requests, and unsupported requests. Each row declares the expected preset, interval, row limit, and ordered tool set.

## Automated checks and recorded outcome

| Layer | What it evaluates | Phase 5 result |
|---|---|---|
| Intent fixture | Tool selection and arguments for 56 prompts | 56/56 passed |
| Follow-up unit case | Prior user intent is reused for “visualize that” | passed |
| Response assembler | Tool numbers win over malicious/fabricated provider text; multiple results produce valid schema | 2/2 passed |
| Tool adapter | Trusted context and limit rejection | 2/2 passed |
| MVC security | Anonymous denial, CSRF, validation/list behavior | 3/3 passed |
| PostgreSQL workflow | Exact `$59.98` paid revenue, persisted follow-up, two audit rows, cross-tenant 404 | passed |
| Frontend unit/service | Input, history, loading/error/retry, metrics/charts, API serialization | 7 Phase 5 tests passed |
| Playwright real stack | Paid-order revenue and contextual visualization follow-up | 1/1 passed |

The complete verification result was 93 backend tests (84 fast/evaluation plus 9 PostgreSQL integration) and 31 frontend tests. These are binary fixture/test outcomes, not a percentage claim about unseen prompts.

## Reproduce

```bash
cd backend
./mvnw verify

cd ../frontend
pnpm check
```

Backend verification requires Docker for PostgreSQL Testcontainers. The default `AI_PROVIDER=mock` requires no key or network. A live provider is deliberately absent from mandatory CI.

## Dimensions covered

- Correct tool name, preset, interval, and row limit.
- Deterministic numeric provenance and response schema validity.
- Date bounds through existing Phase 4 period tests.
- User/store ownership and cross-tenant denial.
- Unsupported non-analytics/mutation prompts.
- Prompt-injection resistance at the numeric output boundary.
- Persistence of messages, safe audit metadata, and follow-up context.
- Provider-independent UI rendering and error behavior.

## Phase 5.5 reliability coverage

`SpringAiProviderFaultTest` runs against MockWebServer and the real Spring AI/OpenAI adapter. It verifies one bounded retry and exactly two total requests for HTTP 429 and 500/502/503; reset recovery; bounded delayed-response timeout; malformed JSON failure without retry; interrupted-body retry limits; safe error codes; and no credential disclosure. `AiOutageIsolationIT` proves a failed AI call does not change product/inventory/order state. Token/cost tests prove provider usage accounting, versioned estimates, unknown-model behavior, telemetry persistence, and prompt-free telemetry shape.

## Known evaluation gaps

- The mock router supports documented phrases and a bounded follow-up heuristic; it is not a semantic model and does not parse arbitrary custom natural-language dates.
- Live OpenAI tool selection, arguments, usage, model metadata, multi-step behavior, and latency remain unverified because no billable evaluation was approved or run in Phase 5.5.
- Mock faults verify client behavior but cannot reproduce every provider edge, proxy, DNS, TLS, or streaming failure.
- The system reports correlations/aggregates only. Causal explanations, forecasts, scenario analysis, persisted reports, and recommended actions belong to Phase 6 or later.
- Current inventory is not historical inventory; days of cover is a selected-period velocity estimate rather than a forecast.

## Optional real-provider evaluation (billable)

The suite is skipped at class level unless `RUN_LIVE_AI_EVAL=true`; ordinary `./mvnw verify` cannot make a paid call. To run it after explicit approval:

```bash
cd backend
RUN_LIVE_AI_EVAL=true \
OPENAI_API_KEY='set-in-shell-only' \
AI_MODEL='gpt-4o-mini' \
AI_LIVE_MAX_COST_USD='0.05' \
./mvnw -Dit.test=LiveOpenAiEvaluationIT verify
```

The suite seeds an isolated H2 tenant and runs exactly three top-level evaluation tasks. Live-evaluation retries are disabled; each task is capped at four tool calls and each provider turn at 300 output tokens, giving a conservative maximum of 15 provider turns across the suite. It compares returned revenue with the deterministic analytics service, checks a multi-tool question and contextual follow-up, and enforces the configured estimated-cost ceiling after the bounded run. It writes sanitized provider/model, latency, token, tool, estimated-cost, success, and failure data to `backend/target/live-ai-evaluation.json`. It never writes a key or prompt. The file is evidence from that particular model/version and time, not a general correctness score.
