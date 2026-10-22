# Phase 5 AI Tool Contracts

## Boundary

`AnalyticsToolAdapter` is the entire model-to-business-data boundary. Tools are read-only wrappers around `AnalyticsService`; they contain no KPI formulas and expose no repository, SQL, mutation, store, user, role, or credential parameter. The orchestrator injects the authenticated `StoreContext` through Spring AI `ToolContext`, which is not part of the model-visible schema.

## Shared period input

All tools accept `preset`: `LAST_7_DAYS`, `LAST_30_DAYS`, `LAST_90_DAYS`, `THIS_MONTH`, `LAST_MONTH`, or `CUSTOM`. `CUSTOM` also requires ISO `startDate` and exclusive `endDateExclusive`. Phase 4 resolves those calendar dates in the store time zone, converts them to UTC half-open instants, and rejects empty or greater-than-366-day ranges.

| Tool | Additional input | Typed result and intended use |
|---|---|---|
| `get_revenue_summary` | none | Revenue, paid orders, AOV, units, currency, period |
| `compare_revenue` | none | Current summary plus the immediately preceding equal-length comparison |
| `get_revenue_trend` | optional `DAY|WEEK|MONTH` interval | Gap-filled revenue/order/unit time series |
| `get_top_selling_products` | `limit` 1–50 | Snapshot product ranking by line revenue |
| `get_sales_by_category` | none | Snapshot category revenue/order/unit ranking |
| `get_order_statistics` | none | Stable alias of the revenue/order overview contract |
| `get_inventory_summary` | none | Current available/reserved/low-stock totals plus period velocity |
| `get_inventory_risk` | `limit` 1–100 | Current product stock, reorder point, velocity, days of cover, and risk |

## Execution contract

1. The adapter parses allow-listed enums/dates and enforces row bounds before calling analytics.
2. Phase 4 applies the authenticated store UUID to every query and calculates values with PostgreSQL `NUMERIC`/Java `BigDecimal`.
3. Each call returns a `ToolEnvelope` with a generated execution ID, exact tool name, typed result, and resolved reporting period.
4. The request-scoped collector retains envelopes for final deterministic assembly.
5. A safe audit row records success/failure, latency, and filter metadata in a short independent transaction.

The global default is at most six tool calls, with at most three calls to one tool. Spring AI parallel tool calls are disabled. Unsupported tools, missing trusted context, invalid parameters, and execution errors fail closed; there is no fallback to generated SQL or invented data.

Transient provider retries restart the request-scoped result collector so abandoned-attempt evidence is not returned to the client. All Phase 5 tools are read-only, which makes a repeated provider attempt incapable of duplicating a commerce operation. A future mutating tool must not inherit this retry contract; it requires an idempotent proposal/approval command boundary.

## Output provenance

`AiResponseAssembler` recognizes only known Phase 4 result types. It copies decimal metrics and chart/table rows from those objects, attaches the matching tool execution ID and period, and emits explicit filters/limitations. Provider text is never parsed for authoritative numbers. A model that writes a plausible but incorrect amount therefore cannot replace the verified amount displayed by the API or UI.

## Security review checklist

- Tool schemas contain no tenant or credential field.
- No tool can write commerce state or execute SQL.
- Every period and row count is bounded.
- Product names and other database text remain data, not instructions.
- Safe logs omit prompts and result payloads.
- Conversation access is separately scoped to authenticated user and store.
- New tools require code review, typed tests, audit metadata review, and response-assembler support.
