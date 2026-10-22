# API Design

## Conventions

- Base path: `/api`; JSON request and response bodies; UTF-8.
- Authentication uses secure HTTP-only session cookies. All state-changing requests require the CSRF token returned by `GET /api/auth/csrf` in the named header (currently `X-XSRF-TOKEN`).
- Collection endpoints use zero-based `page`, bounded `size`, allow-listed `sort`, and filters.
- Commands that may be retried require `Idempotency-Key`.
- IDs use UUID strings. Timestamps use ISO-8601 UTC. Money is serialized as decimal strings or JSON numbers without binary floating-point computation.
- Errors use Problem Details (`application/problem+json`) with stable `type`, `title`, `status`, `detail`, `instance`, and optional field violations/correlation ID.

## Phase 1 contract

### `GET /api/health`

Public liveness endpoint for local integration and container health checks.

Response `200 application/json`:

```json
{
  "status": "UP",
  "service": "stockflow-api",
  "timestamp": "2026-10-20T12:00:00Z"
}
```

`timestamp` is generated in UTC and varies per request.

## Implemented Phase 2 resource surface

| Area | Representative endpoints | Notes |
|---|---|---|
| Auth | `GET /auth/csrf`, `POST /auth/register`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/me` | Registration creates the first store and ADMIN; login optionally selects a membership |
| Store | `GET /store`, `GET/POST /store/members` | Member list/create is ADMIN-only; created members have STAFF role |
| Products | `GET/POST /products`, `GET/PATCH/DELETE /products/{id}` | Delete archives; query supports `q`, category, status, page, size, allow-listed sort/direction |
| Inventory | `GET /inventory`, `GET /inventory/{productId}`, `GET/POST /inventory/{productId}/adjustments` | `lowStockOnly` filter; stock mutations require a reason and are audited |
| Orders | `GET/POST /orders`, `GET /orders/{id}`, `POST /orders/{id}/payment`, `POST /orders/{id}/cancel` | Create requires an 8–200 character `Idempotency-Key`; payment accepts `APPROVED` or `DECLINED` |

All paths above are under `/api`. Except for health and the CSRF bootstrap endpoint, reads require an authenticated session. All POST/PATCH/DELETE calls—including registration and login—require CSRF protection.

## Implemented Phase 4 analytics surface

All analytics reads require an authenticated store membership. `storeId` is never accepted; the server derives it from the session. The complete machine-readable contract is [OpenAPI](openapi.yaml).

| Endpoint | Result | Limits / filters |
|---|---|---|
| `GET /analytics/overview` | Paid revenue, paid orders, AOV, units, and optional previous comparison | Preset/custom range; `comparePreviousPeriod` |
| `GET /analytics/orders/summary` | Alias of the stable overview service contract | Same as overview |
| `GET /analytics/revenue/trend` | Gap-filled revenue/order/unit buckets | Optional `DAY`, `WEEK`, `MONTH` |
| `GET /analytics/products/top` | Snapshot-based product revenue ranking | `limit` 1–50 |
| `GET /analytics/categories/performance` | Snapshot-based category revenue | Bounded date range |
| `GET /analytics/inventory/summary` | Current available/reserved/low-stock totals plus selected-period velocity | Bounded date range |
| `GET /analytics/inventory/risk` | Current product stock, velocity, days of cover, and risk level | `limit` 1–100 |

Shared query parameters are `preset=LAST_7_DAYS|LAST_30_DAYS|LAST_90_DAYS|THIS_MONTH|LAST_MONTH|CUSTOM`, `startDate`, and `endDateExclusive`. Custom dates are required together, use store-local calendar boundaries, and may span at most 366 days. Invalid enum/date/range/limit values return Problem Details with `VALIDATION_FAILED` or a stable analytics range code.

Revenue is JSON decimal data sourced from PostgreSQL `NUMERIC`; JavaScript displays it with the store ISO currency. A null AOV/growth/coverage means the denominator was zero or the measure was unavailable—it is not a measured zero.

## Implemented Phase 5 AI surface

Every AI endpoint requires the current authenticated session. The query endpoint is state-changing because it persists messages and tool audit rows, so it also requires the CSRF token. Conversation identifiers never establish authority: the server loads them by `(id, store_id, user_id)` and returns not found for foreign ownership.

| Endpoint | Result | Limits / behavior |
|---|---|---|
| `POST /ai/query` | A persisted structured analytics response | Body has optional `conversationId` and required `question`; configured question cap defaults to 1,000 characters |
| `GET /ai/conversations?limit=20` | Active owned conversation summaries, newest first | `limit` 1–50 |
| `GET /ai/conversations/{conversationId}` | Up to 200 messages and saved assistant response payloads | Owner and store scoped |

`POST /ai/query` returns `ANALYTICS`, `CLARIFICATION`, or `UNSUPPORTED`, plus typed metrics, line/bar charts, tables, evidence with tool execution IDs and periods, assumptions, limitations, applied filters, and a provider label. All arrays are present even when empty. Authoritative values are copied from validated tool results, not provider prose.

Provider outage/timeout/rate-limit-style failures return Problem Details with HTTP 503 and code `AI_PROVIDER_UNAVAILABLE`. Validation is 400, foreign conversations are 404, and a concurrent conversation update is 409 `CONCURRENT_MODIFICATION`. These failures do not affect the commerce or direct analytics endpoints.

## Implemented Phase 6 business-analysis surface

All report operations require an authenticated session. Create requires CSRF and `Idempotency-Key`; the key is scoped to the authenticated creator and replayed only when the request hash matches.

| Endpoint | Result | Limits / behavior |
|---|---|---|
| `POST /business-analysis/reports` | Synchronously generates and persists a report | Question 1–1,000 chars; explicit/AUTO type; analytics preset/custom dates; at most 5 typed scenarios |
| `GET /business-analysis/reports` | Authorized report summaries, newest first | Optional `status`, optional persisted `type`, `limit` 1–50 |
| `GET /business-analysis/reports/{reportId}` | Metadata plus validated report document when completed | ADMIN store-wide; STAFF creator-only; foreign access is 404 |

Supported persisted types are `REVENUE_INVESTIGATION`, `INVENTORY_RISK`, and `SCENARIO_ANALYSIS`. Scenario percentages are bounded from -90 through 1000. A provider/validation failure returns a safe error and leaves a `FAILED` report with `report=null`; completed reports include schema/report version, periods, freshness, metrics, evidence, findings, hypotheses, recommendations, scenarios, charts, limitations, and model metadata. Retrying a key with a different request returns `409 REPORT_IDEMPOTENCY_CONFLICT`.

### Phase 3 browser integration

- The SPA defaults to same-origin `/api` requests so development and production proxies preserve session-cookie behavior. `VITE_API_BASE_URL` may provide a non-secret build-time base URL.
- Every request uses `credentials: include`. The client obtains a fresh CSRF token before every mutation rather than persisting security tokens in application storage.
- HTTP 401 dispatches a single session-expiry signal that clears tenant-scoped query caches and redirects through the route guard. Login and registration suppress that global path so credential errors stay in their forms.
- Validation violations and stable backend error codes are preserved in `ApiError`; pages render actionable messages for 400, 403, 404, 409, and 5xx responses.
- Order creation retains one random `Idempotency-Key` for the current draft. Button disabling reduces accidental retries, while backend idempotency is the correctness guarantee.
- The frontend does not calculate authoritative order totals or stock. Any preview is advisory; returned server snapshots and balances replace it after mutation.

### Session and CSRF sequence

1. Call `GET /api/auth/csrf` while retaining cookies.
2. Read `headerName` and `token` from the response.
3. Send the token in that header and the cookie jar on the state-changing request.
4. Retain the `JSESSIONID` established after registration/login. Production sets `Secure` through `SESSION_COOKIE_SECURE=true` behind HTTPS.

### Order semantics

- New orders start in `PENDING_PAYMENT` with a 15-minute payment window.
- Approved payment consumes reserved units and transitions to `PAID`.
- Declined payment releases units and transitions to `PAYMENT_FAILED`.
- Cancellation and scheduled expiration release units and transition to `CANCELLED` or `EXPIRED`.
- Repeating the same terminal command/outcome is idempotent. Conflicting terminal transitions return `409 INVALID_ORDER_TRANSITION`.
- Reusing an idempotency key with a different canonical item payload returns `409 IDEMPOTENCY_KEY_REUSED`.

## Planned later resource surface

| Area | Representative endpoints | Notes |
|---|---|---|
| Proposals | `GET /proposals`, `POST /proposals/{id}/approve`, `/reject`, `/execute` | Explicit transition and audit rules |

## Compatibility policy

Breaking response/request changes require a versioning decision. Additive fields are allowed, and clients must ignore unknown fields. OpenAPI generation and broader contract examples remain a later CI-hardening task.
