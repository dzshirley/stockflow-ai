# StockFlow AI Repository Guide

## Scope and architecture

- Build a production-style commerce operations platform; AI augments but never gates core commerce workflows.
- Keep the backend a Java 21/Spring Boot modular monolith organized by business feature under `com.stockflow`.
- Keep the frontend a React/TypeScript single-page application. Put reusable UI in `components`, page composition in `pages`, server calls in `services`, and feature logic in `features` as it grows.
- Do not add independently deployed services without recording a decision in `docs/engineering-decisions.md`.

## Engineering standards

- Keep controllers thin. Put business rules and transactions in services and persistence in repositories.
- Use API DTOs at boundaries; do not expose JPA entities directly.
- Use `BigDecimal`/PostgreSQL `NUMERIC` for money and `Instant`/`TIMESTAMPTZ` for stored timestamps.
- Derive store scope from authenticated server context. Never accept a client or model supplied store ID as authorization.
- Validate all external input and use centralized error responses.
- Consequential AI actions require explicit approval, revalidation, idempotent execution, and an audit record.
- Never commit secrets, generated build output, or local environment files.

## Development workflow

1. Read `docs/implementation-plan.md` and work only in the active phase.
2. Make small, coherent changes and update tests with business logic.
3. Run the relevant backend and frontend checks before marking work complete.
4. Update architecture/API/KPI docs and the engineering decision log when behavior or trade-offs change.
5. Record unverified work honestly; never infer that a build passed.

## Verification commands

- Full local stack: `docker compose up --build`
- Backend: `cd backend && ./mvnw verify`
- Frontend: `cd frontend && corepack enable && pnpm install --frozen-lockfile && pnpm check`
- End-to-end tests (when introduced): `cd frontend && npm run test:e2e`

## Security and testing priorities

- Test cross-store isolation, ownership, authorization, order idempotency, inventory concurrency, state transitions, and transaction rollback.
- Keep AI tools typed, bounded, authorized, and deterministic for metrics. The model may explain verified results but must not perform financial arithmetic or arbitrary SQL.
- Mock external providers in default local and CI workflows.

## Phase 6 and Phase 7 constraints

- Phase 6 is explicitly active and implements read-only evidence-backed report generation. Do not implement Phase 7 proposal, approval, or operational execution workflows unless Phase 7 is separately started.
- Authorize every report operation server-side from `StoreContext`: ADMIN may access every report in the authenticated store; STAFF may access only reports they created; cross-store access is always prohibited.
- Persist reports in PostgreSQL with searchable relational metadata and a validated, versioned JSONB report body. Design indexes for documented list/detail/status retrieval paths rather than speculative JSON searches.
- Keep all authoritative metrics, chart points, calculations, evidence references, assumptions, and limitation semantics server-owned. The model may select only bounded synthesis codes and may not write operational data.
- Use a configurable 90-day retention period as a development default only. Do not automatically delete reports until user/store deletion, audit, backup, and regulatory requirements have an approved policy.
- Keep deterministic mock-provider evaluations mandatory in CI. Live-provider evaluation is opt-in and billable; never run it without explicit approval, and treat real-provider behavior as unverified until evidence from such a run exists.
