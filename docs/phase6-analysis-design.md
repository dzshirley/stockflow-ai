# Phase 6 Evidence-Backed Analysis Contract

This document records the Phase 5.5 preflight contract. Phase 6 now implements it; the current runtime design is documented in [business-analysis-design.md](business-analysis-design.md) and the concrete schema in [report-schema.md](report-schema.md).

## Epistemic layers

1. **EvidenceReference** records a metric value, unit, half-open date range, store time zone, allow-listed filters, source, observation timestamp, and (for analytics-tool evidence) the originating tool execution ID.
2. **BusinessFinding** is either a `VERIFIED_OBSERVATION` or `DETERMINISTIC_CALCULATION`. Every finding cites existing evidence IDs. A deterministic calculation also supplies a reproducible calculation definition.
3. **BusinessHypothesis** is a possible explanation, never a verified fact. It records supporting and counter-evidence, one of `UNTESTED|SUPPORTED|REFUTED|INCONCLUSIVE`, and a concrete validation plan.
4. **Recommendation** cites findings and/or hypotheses, states expected outcome, risks, and prerequisites, and always requires human approval. It does not execute an action.
5. **AnalysisLimitations** identifies data, freshness, method, or interpretation constraints and the findings they affect.

There is deliberately no generic confidence percentage. A percentage is meaningful only when backed by a defined statistical or calibration method. Phase 6 should expose specific uncertainty instead: missing sources, sample size, freshness, conflicting evidence, sensitivity ranges, and untested hypotheses.

## Versioned report object

`BusinessAnalysisReport` carries:

- `reportId`, `schemaVersion`, and immutable `reportVersion`;
- authenticated `storeId` and `userId` ownership;
- lifecycle status: `PENDING`, `RUNNING`, `COMPLETED`, or `FAILED`;
- reporting period as `[periodStart, periodEndExclusive)` plus IANA time zone;
- `dataAsOf` and `createdAt` timestamps;
- evidence, findings, hypotheses, recommendations, and limitations;
- provider/model, prompt version, token usage, pricing version, and estimated—not billed—cost.

The implemented `BusinessAnalysisValidator` rejects missing ownership, invalid/empty periods, duplicate IDs, dangling references, findings without evidence, calculations without definitions, analytics evidence without a tool execution ID, and recommendations that do not require approval.

## Evidence rules for Phase 6

- A finding must never cite prose as evidence. It cites metric records with exact filters and dates.
- Causal language belongs only in `BusinessHypothesis` until a defensible method verifies it.
- A recommendation may depend on a hypothesis, but that dependency remains visible to the reader.
- Source values use decimal types and existing Phase 4 KPI definitions; the model does not recompute financial facts.
- Evidence captures `observedAt`/`dataAsOf`, so a report can be recognized as stale after commerce data changes.
- Authorization is evaluated from the authenticated context on creation and retrieval; IDs in a report body are not an authorization source.

## Confirmed report authorization

- The server derives the actor's user, store, and role from `StoreContext`; request fields and the JSONB report body never grant access.
- An ADMIN may create, list, and read every report belonging to the authenticated store.
- A STAFF user may create reports and may list or read only reports whose creator is that authenticated user.
- Every repository lookup includes `store_id`. STAFF lookups also include `created_by_user_id`; an unauthorized or cross-store report is indistinguishable from a missing report.
- Background generation must retain the report's persisted store and creator attribution, but workers do not bypass the same ownership rules at the API boundary.

## Historical implementation boundary

At Phase 5.5 signoff, only contracts and validation existed. Phase 6 subsequently added the controller, services, bounded synthesis prompt, repository, and Flyway V6 report table. This historical note must not be used as current feature status.
