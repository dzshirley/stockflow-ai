# Phase 6 Business Analysis Design

## Runtime flow and trust boundary

Phase 6 adds a synchronous, read-only report workflow to the existing modular monolith. A request is authenticated, classified into one supported analysis type, calculated by deterministic services, reduced to a bounded aggregate signal bundle, synthesized through the configured Phase 5 provider, validated, and persisted.

```text
Browser -> BusinessAnalysisController -> BusinessReportService
                                      -> BusinessAnalysisEngine -> AnalyticsService + store-scoped SQL
                                      -> BusinessAnalysisAiService -> configured provider
                                      -> BusinessReportAssembler -> BusinessAnalysisValidator
                                      -> BusinessReportPersistenceService -> PostgreSQL JSONB
```

The model never receives a store ID, database access, arbitrary SQL, row-level order data, or a mutation tool. It can select only allow-listed hypothesis and recommendation codes. The server constructs all prose templates, numbers, evidence IDs, chart points, assumptions, limitations, and recommendation links. A malformed or invented code fails the report rather than becoming content.

Supported analysis types are `REVENUE_INVESTIGATION`, `INVENTORY_RISK`, and `SCENARIO_ANALYSIS`. `AUTO` is resolved deterministically from bounded intent phrases. Explicit mutation, arbitrary SQL, individual-buyer prediction, and exact-future-forecast requests are rejected. Phase 6 has no business-operation execution path.

## Deterministic methods

All revenue inputs include only `PAID` orders and use immutable order-item price, discount, category, and cost snapshots. Periods are half-open store-local ranges, `[start, end)`, and comparisons use the immediately preceding range of equal duration.

Revenue change is `current revenue - previous revenue`; percentage change is that difference divided by the absolute previous value. A zero previous value produces an unavailable percentage, not infinity or an invented rate. Product and category contribution is `current dimension revenue - previous dimension revenue`. New and discontinued dimensions remain in the union. The residual is total revenue change minus the sum of contributions and is always exposed.

Price-volume decomposition is calculated per product, avoiding aggregation of heterogeneous products. Continuing products use a symmetric Shapley/midpoint decomposition:

```text
price effect  = (P1 - P0) * (Q1 + Q0) / 2
volume effect = (Q1 - Q0) * (P1 + P0) / 2
```

`P` is realized net unit price after recorded line discount and `Q` is paid quantity. New and discontinued products are isolated as lifecycle effects because one period lacks an observed price. The server exposes price, volume, new-product, discontinued-product, and rounding-residual components. This is descriptive decomposition, not demand elasticity or causal inference. Returns are explicitly unavailable because the commerce lifecycle has no return/refund state.

Inventory diagnostics combine current available/reserved balances with selected-period and prior-period paid-unit sales. Daily velocity is current paid units divided by selected period days. Days of cover is current available quantity divided by that velocity. Zero recent sales is reported as `NO_SALES`, not zero demand. Current risk uses explicit `OUT_OF_STOCK`, `LOW_STOCK`, `AT_RISK`, `HEALTHY`, and excess-inventory signals. Historical stockout duration and valid turnover are unavailable until snapshot/average-inventory history exists.

Typed scenarios support unit-sales, price, recorded-discount, and sales-velocity percentage changes. Inputs are bounded to -90% through 1000%. Each result includes the baseline, modeled value, assumptions, limitations, and gross-profit result when cost evidence supports it. Scenarios hold other stated variables constant and are never labeled forecasts.

## Evidence and diagnosis rules

The report separates verified observations, deterministic calculations, untested hypotheses, advisory recommendations, and limitations. Every metric and finding links to server-created `EvidenceReference` records containing a metric key, value, unit, exact period, time zone, filters, source, and observation time. `BusinessAnalysisValidator` rejects duplicate or dangling IDs, calculations without definitions, findings without evidence, unsupported recommendation links, incomplete scenarios, and recommendations that do not require human approval.

The LLM cannot author evidence IDs or financial values. It receives only aggregate direction/count/residual signals and returns a short JSON allow-list selection. Hypotheses remain `UNTESTED` unless a future defensible method changes their status. No generic confidence percentage is emitted. Unsupported causation is qualified with both a hypothesis and a limitation.

Recommendation priority is deterministic and transparent: a revenue decline of at least 10% is high priority, another non-zero revenue change is medium, and no change is low. Verified current inventory risk is high priority; slow-moving monitoring is low. Expected financial impact is `null` unless directly estimable, and all recommendations are advisory with `approvalRequired=true`.

## Failure, concurrency, and observability

Report metadata is committed as `PENDING`, then transitioned in independent short transactions through `RUNNING` to `COMPLETED` or `FAILED`. A completed document is stored only after schema validation. Provider, serialization, or validation failure leaves an explicit failed row with no fabricated document. Optimistic locking prevents conflicting lifecycle updates. A creator-scoped idempotency key plus request hash replays the same report and rejects a different payload.

Each report calculation uses one read-only PostgreSQL `REPEATABLE READ` snapshot so its multiple aggregate queries cannot observe different commerce commits. PostgreSQL MVCC keeps this from taking write locks or blocking core commerce workflows. The existing provider gateway owns one bounded transient retry and its transport timeouts. Metrics record report duration/outcome, analytics duration, model latency/errors, invalid synthesis, tokens, and estimated—not billed—cost. Report metadata stores provider/model/prompt, usage, pricing version, cost estimate, and model latency. Prompts, raw responses, credentials, and business rows are not logged.

## Known limits

- Real-provider behavior is unverified because no billable live evaluation was authorized.
- A report is a point-in-time analytical snapshot; it does not automatically refresh when commerce changes.
- There is no causal inference, demand forecast, elasticity model, seasonality model, stockout history, return/refund analysis, or average-inventory turnover.
- Generation is synchronous for the current bounded workload. A queue is not justified until measured latency or throughput requires it.
- Phase 7 proposal, approval, revalidation, execution, and operational audit workflows are intentionally absent.
