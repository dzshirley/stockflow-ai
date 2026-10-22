# Product Requirements

## Product statement

StockFlow AI is a multi-tenant commerce operations platform for small and medium online retailers. It combines reliable product, inventory, order, and analytics workflows with a bounded AI copilot for data analysis, business diagnosis, and approval-gated operational proposals.

The commerce application must remain usable when the model provider is unavailable.

## Users and roles

- **Admin:** owns or administers a store, manages members and settings, and can approve consequential proposals.
- **Staff:** performs day-to-day product, inventory, order, analytics, and permitted proposal workflows.

Every business resource belongs to one store. Membership derived from authenticated server state is the authorization boundary.

## Functional scope

1. Register, sign in/out, and enforce role- and store-scoped access.
2. Search, filter, sort, page, create, update, and archive products with store-local SKU uniqueness.
3. Track available/reserved inventory, adjustments, audits, and low-stock conditions without overselling.
4. Create idempotent orders, reserve/release stock, mock payment, expire orders, and enforce explicit state transitions.
5. Report revenue, orders, average order value, units sold, trends, product/category performance, and inventory risk.
6. Answer supported natural-language analytics questions through authorized typed tools and structured responses.
7. Persist evidence-backed business reports that distinguish facts, findings, hypotheses, and recommendations.
8. Draft restock proposals; require approval and fresh validation before idempotent execution.

## Phase 0-1 scope

- Monorepo foundations and persistent documentation.
- Java 21/Spring Boot API with PostgreSQL/Flyway configuration.
- React/TypeScript/Vite application that queries `GET /api/health` through TanStack Query.
- Docker Compose local environment and initial schema migration.
- Backend and frontend automated smoke tests and CI checks.

Explicitly excluded until later phases: authentication behavior, commerce CRUD, analytics calculations, AI/provider integration, RabbitMQ, Redis, real payments, and cloud provisioning.

## Non-functional requirements

- **Correctness:** atomic inventory updates, deterministic financial calculations, idempotent commands, and explicit order/proposal states.
- **Security:** least privilege, BCrypt/approved password hashing, input validation, ownership isolation, no browser secrets, and auditable consequential changes.
- **Availability:** health probes and graceful AI degradation; commerce does not depend on AI availability.
- **Performance:** paginated reads, bounded AI tools, indexed ownership/query paths, and measurable load targets before launch.
- **Maintainability:** package-by-feature modular monolith, DTO/entity separation, migrations, decision records, and automated tests.
- **Observability:** structured logs, request correlation, health/readiness signals, and later metrics for queues, AI latency/cost, and failures.
- **Portability:** local startup with Docker Compose and immutable production containers.
- **Accessibility:** semantic controls, keyboard operation, visible focus, sufficient contrast, and clear loading/error states.

## Product-level acceptance criteria

The definition of done is tracked by phase in `implementation-plan.md`. At product completion, all critical commerce, analytics, AI approval, isolation, concurrency, and CI requirements in the project brief must be demonstrably tested. A live AWS deployment requires separate approval.
