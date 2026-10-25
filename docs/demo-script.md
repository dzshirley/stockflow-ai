# StockFlow AI — 2–3 Minute Portfolio Demo

Use only the synthetic `DEMO-*` workspace. Do not show credentials, browser
storage, CloudWatch secrets, database connection strings, or real business data.

## 0:00–0:25 — Problem and architecture

“StockFlow AI was motivated by operational problems I encountered around a
K-Pop merchandise business with experience across more than 30,000 historical
transactions and 200 SKUs. This portfolio application is not connected to that
store; it uses synthetic data. I built one system for commerce operations,
deterministic analytics, evidence-backed AI, and safely approved inventory work.”

Point out that React calls one Spring Boot modular monolith and PostgreSQL owns
transactional state. AI is optional and never gates orders or inventory.

## 0:25–0:55 — Commerce and inventory

Open Dashboard, then Products/Inventory. Show synthetic products, current
available/reserved counts, reorder thresholds, and an adjustment audit entry.
Open an order and explain that order lines snapshot product/price/cost, order
creation is idempotent, and inventory is reserved atomically.

“Two concurrent buyers cannot both reserve the last unit because the service
locks inventory in deterministic order and applies conditional updates inside
one PostgreSQL transaction.”

## 0:55–1:20 — Deterministic analytics

Open Analytics. Show paid revenue, orders, units, AOV, comparison period, trend,
rankings, and inventory risk.

“Every financial value is calculated in Java/SQL from paid-order snapshots with
BigDecimal. A model never performs the authoritative arithmetic.”

## 1:20–1:45 — AI Data Analyst and Business Insights

Ask: “Show revenue for the last 30 days,” then show the typed metric/evidence
response. Open Business Insights and select the prepared inventory-risk report.
Point to verified findings, calculations, hypotheses, recommendations, evidence
references, and limitations.

“The model can select only allow-listed synthesis codes. The server owns all
numbers, evidence IDs, date ranges, and authorization.”

## 1:45–2:20 — Human-approved operation

Open Restock Operations and select an unapproved synthetic proposal. Show its
source report, inventory snapshot, assumptions, and quantity calculation.
Approve as ADMIN, but explain that approval is not an inventory mutation. Check
physical receipt confirmation and record the receipt. Return to Inventory and
show the exact increment and audit entry.

“Execution revalidates the report, evidence hash, product, inventory version,
current risk, and user role. A unique command makes replay safe; inventory,
proposal state, execution attempt, audit, and outbox record commit atomically.”

## 2:20–2:45 — Trade-off and evidence

“I kept this a modular monolith because cross-feature transactions are central
and the demo traffic does not justify distributed failure modes. The outbox is
durable but has no relay because there is no consumer—I did not add RabbitMQ as
decoration.”

Close with the verified facts: 44 endpoints, 245 discovered automated cases,
244 executed/passed with one billable live test skipped, three real-stack E2E
workflows, and the documented local 4.32 ms p50 / 26.74 ms p95 / 83.22 ms p99
mixed workload. Clarify that AWS deployment is prepared but not live unless a
verified URL has subsequently been added.

## Questions to expect

- Why sessions instead of JWT? Server-side revocation and a first-party SPA are
  simple here; horizontal scaling requires a shared session decision.
- What fails if AI is down? AI synthesis returns a scoped error; commerce and
  direct analytics remain available.
- Is this production-ready? No. Local correctness evidence is strong, while a
  live launch still needs approved spend, remote CI/CD evidence, staging/restore
  drills, backend SCA, and deployment verification.
