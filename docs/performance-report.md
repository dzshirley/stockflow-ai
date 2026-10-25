# Phase 8 Performance Report

Verified on 2026-10-23. These measurements are repeatable local smoke results,
not production service-level objectives or capacity claims.

## Test conditions

- Docker Desktop 29.9.1 on the development workstation, with 7,934 MB assigned
  to the Docker VM.
- Production-style Nginx frontend, Spring Boot backend, and PostgreSQL 18 from
  `docker compose`; the deterministic mock AI provider was used.
- `scripts/performance/phase8-api-smoke.js` created an isolated tenant, two
  products, 100 paid seed orders, one completed inventory report, and one
  approved proposal.
- Five virtual users ran for 15 seconds. Each iteration read products,
  inventory, two analytics resources, a persisted report, and proposals, then
  created and cancelled a uniquely idempotent order. A separate one-iteration
  scenario confirmed one physical receipt.
- Thresholds were 1% maximum HTTP errors, 1,000 ms overall/write p95, and 750
  ms read/auth p95. Run with a current k6 image against the local stack:

```bash
docker run --rm -i -v "$PWD/scripts/performance:/scripts" grafana/k6:latest run \
  -e BASE_URL=http://host.docker.internal:5173 \
  -e VUS=5 -e DURATION=15s -e DATASET_ORDERS=100 \
  /scripts/phase8-api-smoke.js
```

## Results

The initial acceptance run completed 5,426 HTTP requests at 320.43
requests/second with no failed requests or checks. Overall latency was 2.98 ms
p50 and 12.17 ms p95; maximum observed latency was 345.17 ms. The 501 completed
scenario iterations ran at 29.59 iterations/second.

A subsequent comparable run added explicit p99 output. It completed 4,316 HTTP
requests at 225.07 requests/second with a 0.00% error rate and 4,110/4,110
passing checks. Overall latency was 4.32 ms p50, 26.74 ms p95, 83.22 ms p99,
and 853.88 ms maximum. It completed 390 scenario iterations at 20.34/second.
Both runs passed the same thresholds. The difference is retained as evidence of
local workstation/Docker variance, not presented as a regression or SLO.

| Endpoint family | Samples represented | p50 | p95 | p99 |
|---|---|---:|---:|---:|
| Login | one login per load VU plus receipt VU | 396.09 ms | 403.59 ms | 404.27 ms |
| Product list | each load iteration | 3.58 ms | 18.27 ms | 105.25 ms |
| Inventory balance | each load iteration | 3.26 ms | 17.97 ms | 46.38 ms |
| Analytics overview + trend | two per load iteration | 4.35 ms | 18.79 ms | 43.23 ms |
| Order create + cancel | two per load iteration | 11.05 ms | 50.76 ms | 122.33 ms |
| Persisted report retrieval | each load iteration | 4.57 ms | 23.63 ms | 72.43 ms |
| Proposal list | each load iteration | 4.08 ms | 16.81 ms | 39.55 ms |
| Receipt confirmation | one safety workflow sample | 28.06 ms | 28.06 ms | Not meaningful |

The endpoint table uses the p99-enabled run. The receipt value is a single
observation and must not be interpreted as a distribution. Login intentionally
includes BCrypt work and is expected to be materially slower than authenticated
reads.

A one-shot `docker stats` sample during the p99 run observed the backend at
175.09% CPU and 518 MiB, PostgreSQL at 23.44% CPU and 69.71 MiB, Nginx at 8.31%
CPU and 11.06 MiB, and k6 at 11.71% CPU and 69.13 MiB. Container CPU may exceed
100% when more than one core is used. This point sample is not an average,
saturation curve, or capacity limit.

An intermediate run reused a fixed receipt command UUID and correctly received
HTTP 409 after that global operation command had already been used by a prior
synthetic tenant. The benchmark was corrected to use the proposal UUID as its
unique command ID; the final run above is the acceptance result. This was a
workload defect, not a duplicate inventory effect.

## PostgreSQL query evidence

`EXPLAIN (ANALYZE, BUFFERS)` for the paid-sales aggregate against the synthetic
tenant completed in 0.623 ms with 45 shared-buffer hits and no disk reads. The
dataset held 100 qualifying paid orders, 854 tenant order rows encountered by
the chosen store index, and 911 total `order_items` rows. PostgreSQL chose the
existing unique store/order index and an in-memory hash join at this small
cardinality rather than the `(store_id, status, paid_at)` analytics index.

No missing index is justified by this dataset: API p95 remained below 19 ms and
the measured aggregate completed below 1 ms. At materially larger data volumes,
capture plans again before adding indexes, caching, or materialized views.

## Frontend bundle

The pre-split baseline was 777.47 kB minified / 225.15 kB gzip. Route-level
lazy loading reduced the production initial entry to 257.19 kB / 79.56 kB gzip,
a 66.9% minified reduction. The largest on-demand chart chunk is 357.55 kB /
103.52 kB gzip.

`pnpm check` now enforces a raw-minified budget of 300 KiB for the initial
entry and 400 KiB for every JavaScript chunk. This budget catches regressions
without adding a bundle-analysis service or forcing speculative dependency
replacement.

## Interpretation and next work

The local smoke profile found no current latency or error-rate blocker. It does
not cover WAN latency, TLS termination, multi-instance sessions, large tenants,
long reporting periods, autoscaling, or sustained soak behavior. Before a real
production launch, repeat against production-shaped staging data, define SLOs
and saturation limits, and profile PostgreSQL CPU/I/O and connection-pool wait
time under longer mixed workloads.
