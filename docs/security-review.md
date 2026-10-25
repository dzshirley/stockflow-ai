# Phase 8 Security Review

Reviewed on 2026-10-23. This is an engineering hardening review, not a formal
penetration test, compliance certification, or production risk acceptance.

## Controls verified or added

- Authentication continues to use BCrypt cost 12, server-side sessions,
  session-fixation protection, HttpOnly/SameSite cookies, CSRF on mutations,
  and generic invalid-credential responses.
- Login failures are now throttled by a bounded, privacy-minimized in-process
  guard keyed by a SHA-256 digest of normalized email plus remote address. The
  default is five failures in 15 minutes with at most 10,000 tracked keys;
  successful login clears the key. HTTP 429 is returned without logging the
  password or raw key.
- Store and role authorization remains server-derived from `StoreContext`.
  New MVC checks cover anonymous operations access, CSRF/idempotency
  requirements, bounded list input, and receipt body validation. Existing
  PostgreSQL and E2E coverage verifies creator/store isolation and ADMIN-only
  consequential execution.
- Request bodies are Bean-Validation bounded. Tomcat and Nginx now enforce a
  1 MiB body limit and Tomcat limits request headers to 16 KiB.
- The OpenAI credential remains server-only. Provider faults return safe codes;
  prompts, model bodies, passwords, session cookies, and credentials are not
  written by request observability.
- A repository scan found no common AWS/OpenAI/private-key credential pattern.
  Only `.env.example` is present; local `.env`, build output, reports,
  dependencies, and OS artifacts are ignored.
- `pnpm audit --prod --audit-level high` reported no known production frontend
  vulnerabilities. Dependabot now covers Maven, npm, Dockerfiles, and GitHub
  Actions weekly. CI repeats the production npm audit and a common-secret scan.

## Boundary review

The intended browser deployment is same-origin through Nginx; no permissive
application CORS policy is configured. Cookies must be marked `Secure` in an
HTTPS deployment through `SESSION_COOKIE_SECURE=true`. CSRF is not disabled for
JSON endpoints. Cross-store resource IDs return not found rather than leaking
ownership. The model never receives a caller-selected store ID and cannot
execute inventory changes.

Consequential restock receipt remains protected by all of: authenticated ADMIN
role, CSRF, explicit physical-receipt confirmation, approved state, current
proposal version, evidence/report/inventory freshness revalidation, UUID command
idempotency, a database transaction, and audit/outbox writes.

## Findings and limitations

| Severity | Finding | Status / action |
|---|---|---|
| Medium | Login throttling is process-local and resets on restart; separate instances do not share counters. | Adequate development defense. Add edge/WAF or shared enforcement before a horizontally scaled public deployment. |
| Medium | Server sessions are local to one application instance. | Use sticky sessions or a reviewed shared session store before scaling horizontally. |
| Medium | Production TLS, security headers at the edge, secret rotation, backup encryption, and network policies are deployment responsibilities and are not exercised locally. | Required in a real deployment review. |
| Medium | Maven dependencies do not yet have a blocking vulnerability scanner in the local build. | Dependabot covers Maven updates; add an approved SCA tool with a triage policy before production. |
| Low | GitHub Actions are version-tag pinned rather than immutable commit-SHA pinned. | Pin action SHAs if the repository's supply-chain policy requires it. |
| Low | A 1 MiB global request limit is intentionally coarse. | Revisit only if a legitimate upload/API requirement appears. |
| Informational | No formal DAST, penetration test, browser CSP rollout, or regulatory assessment was performed. | Explicitly outside this local hardening milestone. |

No Critical or High finding was identified in the reviewed application code.
That statement is scoped to the checks above and is not proof that none exists.

## Phase 9 Stage A deployment addendum

The prepared AWS design preserves one browser origin through CloudFront, keeps
S3 and PostgreSQL non-public, restricts the ALB to CloudFront origin ranges plus
a generated header, injects the RDS-managed credential at runtime, and uses a
read-only container filesystem except for `/tmp`. Public registration and paid
AI default off. GitHub deployment is manual, protected-environment gated,
immutable-SHA addressed, and assumes a scoped role with short-lived OIDC rather
than a stored AWS key.

Local verification confirmed CSP, frame, referrer, content-type, permissions,
and cache headers from the Nginx image. CloudFront additionally prepares HSTS.
The production profile uses secure session cookies, hidden health details,
bounded database connections/timeouts, and graceful shutdown. The unused Spring
default-user auto-configuration was excluded so startup does not generate or log
an irrelevant password.

These controls are code/design evidence only. An AWS plan, IAM role assumption,
TLS/DNS path, security groups, alarms, ECR findings, secret rotation, backup
restore, and public abuse behavior remain unverified until an approved Stage B.
Database transport is encrypted with `sslmode=require`; hostname/CA validation
with an RDS CA bundle is a documented pre-launch hardening decision. The
Single-AZ/one-task design is cost-conscious, not highly available.
