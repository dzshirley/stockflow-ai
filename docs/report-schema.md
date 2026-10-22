# Business Analysis Report Schema

`BusinessAnalysisReport` schema version 1 is the canonical completed report document. PostgreSQL stores the validated document as JSONB and projects retrieval, ownership, lifecycle, freshness, and model metadata into relational columns.

## Document sections

| Section | Contract |
|---|---|
| Identity | `reportId`, positive `schemaVersion`, positive `reportVersion`, `reportType`, title |
| Ownership | authenticated `storeId` and `createdByUserId`; never accepted as request authorization |
| Lifecycle | `PENDING`, `RUNNING`, `COMPLETED`, or `FAILED`; JSONB is present only for completed reports |
| Periods | current and comparison half-open local dates, IANA time zone, currency, `dataAsOf` |
| Metrics | current/comparison/absolute/percentage values plus one or more evidence IDs |
| Evidence | source, metric key, decimal value, unit, period, time zone, allow-listed filters, observation time |
| Findings | `VERIFIED_OBSERVATION` or `DETERMINISTIC_CALCULATION`; the latter includes its method |
| Hypotheses | qualified statement, rationale, supporting/counter evidence, status, validation plan |
| Recommendations | rationale, finding/hypothesis/evidence links, priority, nullable impact, assumptions, risks, next steps, approval flag |
| Scenarios | typed percentage input, baseline/modeled primary value, optional gross-profit values, assumptions and limitations |
| Charts | server-built bar/waterfall points; browsers never parse model prose into numbers |
| Limitations | stable code, description, and affected finding IDs |
| Model metadata | provider/model/prompt, provider-reported tokens, pricing version, estimated cost, latency |

Money and calculated ratios use Java `BigDecimal` and PostgreSQL `NUMERIC`; timestamps use `Instant` and `TIMESTAMPTZ`. A percentage is `null` when the prior denominator is zero. An unavailable estimate remains `null` and must not be replaced by zero.

## Referential validation

Completed reports must have unique evidence, finding, hypothesis, recommendation, and scenario IDs. Metrics, findings, hypotheses, recommendations, scenarios, and limitations can reference only IDs contained in the same report. Every finding and recommendation requires evidence. Deterministic findings require a calculation definition. Every recommendation requires human approval. Analytics-tool evidence requires its durable tool-execution ID; Phase 6's direct query snapshots use `ORDER_SNAPSHOT` or `INVENTORY_SNAPSHOT` instead.

The persisted relational ownership/status/period/version columns and JSONB equivalents are written from the same server object. Retrieval deserializes and validates the JSONB document again; corruption maps to a safe conflict response.

## Persistence and access

Flyway V6 creates `business_analysis_reports`. ADMIN list/detail predicates always include `store_id`. STAFF predicates include both `store_id` and `created_by_user_id`; inaccessible IDs return not found. Composite indexes serve those two list paths and store/status filtering. There is no speculative JSONB GIN index.

The configurable 90-day `retention_until` value is a development default only. It does not trigger deletion. Production policy still requires decisions for user deletion, store deletion, audit preservation, legal holds, backups, and regulatory obligations.

## Version evolution

`schemaVersion` changes for incompatible document-contract changes. `reportVersion` identifies content revision. Phase 6 creates immutable version 1 reports; regeneration uses a new report ID rather than overwriting evidence. A future in-place revision design must preserve older documents and add an explicit `(id, report_version)` key before claiming revision history.
