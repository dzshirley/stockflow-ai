# Phase 6 Report Persistence Design

This document records the Phase 5.5 target design. Phase 6 implements the core design in Flyway V6 and the `analysis` package; see [report-schema.md](report-schema.md) for the current contract. Any statements below phrased as future design describe the pre-implementation decision record.

## Recommended shape

Start with one `business_analysis_reports` table rather than normalizing every finding and evidence item prematurely:

| Column | Purpose |
|---|---|
| `id UUID` | Stable report ID |
| `store_id UUID`, `created_by_user_id UUID` | Tenant and creator ownership |
| `status VARCHAR` | `PENDING|RUNNING|COMPLETED|FAILED` |
| `schema_version INT`, `report_version INT` | Document compatibility and immutable revision |
| `period_start DATE`, `period_end_exclusive DATE`, `time_zone VARCHAR` | Exact reporting window |
| `data_as_of TIMESTAMPTZ` | Data freshness boundary |
| `report_document JSONB` | Validated `BusinessAnalysisReport` snapshot |
| `model_provider`, `model_name`, `prompt_version` | Reproducibility metadata |
| token/cost columns | Provider-reported tokens, pricing version, estimated cost |
| `content_hash VARCHAR` | Integrity/deduplication aid |
| `created_at`, `completed_at`, `retention_until` | Lifecycle and retention |

PostgreSQL remains the system of record. Keep report metadata in relational columns and the validated, versioned report body in JSONB. The document must include its schema version, reporting period, evidence references, lifecycle status, freshness, and model metadata even where selected values are also projected into columns for querying.

Initial indexes follow the approved retrieval paths:

- primary/unique revision lookup on `(id, report_version)`;
- ADMIN list pagination on `(store_id, created_at DESC, id)`;
- STAFF list pagination on `(store_id, created_by_user_id, created_at DESC, id)`;
- status filtering on `(store_id, status, created_at DESC, id)` only when the implemented status/queue endpoint needs it.

Do not add a generic JSONB GIN index or normalized child tables until a measured cross-report evidence/finding query requires them. Detail and status lookups still include `store_id`, even when `id` is globally unique.

## Versioning and consistency

- Treat a completed revision as immutable. Regeneration creates `report_version + 1`; it does not overwrite prior evidence.
- Validate the Java document and ownership before persistence. The database stores the validated document plus searchable ownership/lifecycle columns.
- `schema_version` changes only when the document contract changes; `report_version` changes when the analysis content changes.
- Evidence should reference immutable metric snapshots or durable tool-execution IDs. If durable references cannot guarantee reproducibility, embed the validated metric snapshot in the report document.
- Use optimistic locking for status transitions. Only one transition may complete a particular revision.
- Report generation remains read-only. Future actions must use the separate proposal/approval workflow and revalidate freshness before execution.

## Access control

Every create/list/get query includes authenticated `store_id`. ADMIN users may access all reports in that store. STAFF queries additionally require `created_by_user_id = authenticated user_id`. This role decision is performed server-side and is covered by explicit repository/service tests; it must never be implemented by merely hiding UI controls. Cross-store and unauthorized IDs return not-found behavior. Model- or client-supplied owner IDs are ignored.

## Retention and privacy

- Use a configurable 90-day initial retention period and populate `retention_until` from the creation timestamp. This is a development default, not an approved production or compliance policy.
- Do not enable automatic deletion yet. Before a retention worker exists, approve the behavior for user deletion, store deletion, audit-record preservation, legal/compliance holds, and applicable retention obligations.
- Store deletion must not cascade reports until the store-deletion and audit policy is defined. User deletion may retain a report under store ownership while pseudonymizing creator attribution only if the eventual legal and product policies allow it.
- Do not persist prompts, API keys, session data, raw provider traces, or unrestricted business rows inside reports.
- A future retention job should delete expired revisions in bounded batches and emit audit metadata/metrics, not business content; `retention_until` alone does not cause deletion.
- Backups inherit the same access and expiry policy; application deletion cannot promise immediate removal from immutable backup media.

## Retrieval API target

A later Phase 6 API can expose create/status/list/detail endpoints with bounded pagination and an explicit schema version. Failed reports should return safe error codes and limitations, not provider stack traces. No endpoint or migration is implemented in Phase 5.5.
