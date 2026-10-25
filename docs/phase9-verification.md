# Phase 9 Stage A Verification

Verified: 2026-10-24. Scope: deployment preparation only. No AWS API call,
Terraform plan/apply, domain purchase, public endpoint, paid model request, or
remote deployment occurred.

## Outcome

Stage A prepares a production profile, costed AWS architecture, Terraform,
approval-gated CI/CD, observability, synthetic demo bootstrap, operational
documentation, recruiter-facing README, and working-application screenshots.
The local release gate is green. Stage B remains blocked on explicit owner
approval of architecture, approximately $88–95/month recurring cost, account,
region, domain/public exposure, budget, and retention policy.

## Automated application results

| Gate | Discovered | Executed | Passed | Failed | Skipped |
|---|---:|---:|---:|---:|---:|
| Maven Surefire | 172 | 172 | 172 | 0 | 0 |
| Maven Failsafe | 29 | 28 | 28 | 0 | 1 |
| **Backend JUnit total** | **201** | **200** | **200** | **0** | **1** |
| Vitest | 41 | 41 | 41 | 0 | 0 |
| Playwright Chromium | 3 | 3 | 3 | 0 | 0 |
| **Unique total** | **245** | **244** | **244** | **0** | **1** |

The skipped case is `LiveOpenAiEvaluationIT`, whose explicit opt-in guard
prevented any paid request. All 27 PostgreSQL Testcontainers cases are included
inside Failsafe and passed; they are not double-counted above. The only new
Stage A runner case verifies that server-side registration disablement returns
403 without invoking registration business logic.

Commands:

```bash
cd backend && ./mvnw --batch-mode clean verify
cd frontend && pnpm install --frozen-lockfile && pnpm check
cd frontend && pnpm test:e2e
```

The backend ran in an Eclipse Temurin Java 21 container against PostgreSQL 18
Testcontainers because the host default Java is not the project runtime.
Frontend checks passed strict TypeScript, ESLint with zero warnings, all 41
Vitest cases, production build, and bundle budgets. The production entry remains
257.19 kB minified / 79.56 kB gzip; the largest lazy chunk is 357.56 kB /
103.52 kB gzip.

## Deployment artifact verification

- `terraform fmt -check -recursive` passed with Terraform 1.13.
- `terraform init -backend=false` installed the locked providers without an AWS
  backend or credentials; `terraform validate` passed.
- `actionlint` 1.7.10 and independent YAML parsing passed all CI/CD workflows.
- `sh -n` passed all three demo scripts.
- `docker compose config --quiet` passed.
- Backend, frontend, and one-off PostgreSQL seed images built successfully.
- Rebuilt Compose reported PostgreSQL, backend, and frontend healthy; direct
  Actuator readiness and proxied `/api/health` returned `UP`.
- An isolated backend container started with the `prod` profile, validated all
  nine Flyway migrations, and returned readiness `UP`. Its startup log contained
  no Spring fallback generated-password warning after the unused default-user
  auto-configuration was excluded.
- Flyway schema history reported V9 successful and Hibernate validated the
  runtime schema on startup.
- `index.html` returned `no-cache, no-store`; hashed JavaScript returned a
  one-year immutable cache policy. Both returned CSP, referrer, content-type,
  frame, and permissions headers. HSTS is applied only at the proposed HTTPS
  CloudFront edge, not the local HTTP server.
- The controlled local bootstrap created a synthetic ADMIN/store, and the
  one-off image inserted six `DEMO-*` products plus 120 deterministic orders.
- The mock workflow created a verified AI conversation, completed inventory
  report, and three unapproved proposals. An initial rehearsal found and fixed
  recommendation selection that depended on list order; the script now joins
  recommendation references to typed inventory evidence IDs.
- Seven screenshots were captured from the running app through the browser:
  dashboard, inventory, orders, analytics, AI response, saved evidence report,
  and proposed restock operations. They are application evidence, not mockups.

Three deployment-preparation defects were caught and corrected by this gate:
Hikari timeout values must be integer milliseconds; Nginx location-level cache
headers otherwise suppress inherited security headers; and Spring's unused
default-user auto-configuration emitted a generated password despite StockFlow's
custom session authentication and disabled Basic/form login.

## Security and operational review

- Production registration defaults to disabled in both backend and frontend.
  The bootstrap exception is temporary and server-authorized, not a UI control.
- RDS is non-public; security groups permit database ingress only from ECS and
  ALB ingress only from CloudFront plus a generated origin header.
- GitHub deployment uses repository/environment-scoped OIDC, least-privilege
  artifact/service permissions, a protected environment, full immutable commit
  SHA, and typed manual confirmation. No long-lived AWS key is configured.
- Database credentials come from the RDS-managed secret. Terraform state,
  plans, tfvars, `.env`, credentials, generated artifacts, and test output are
  ignored from Git.
- Logs exclude prompts, response bodies, cookies, passwords, and credentials.
  Safe events cover provider, report, and receipt failures; successful provider
  events carry latency/tool/token/pricing/estimated-cost metadata only.
- CloudWatch configuration covers target health, API 5xx/p95, database CPU,
  connections/storage, provider/report/receipt failures, and report/proposal
  recovery. Budget notifications are configured for 50%, 80%, and 100%.
- The outbox has no relay or consumer, so no delivery guarantee or alarm is
  falsely claimed beyond atomic durable PostgreSQL recording.

## Unverified behavior and limitations

- No authenticated Terraform plan or AWS resource creation was performed.
  Account quotas, IAM integration, certificate validation, DNS propagation,
  ECS/RDS behavior, CloudFront routing, alarms, OIDC assumption, remote GitHub
  Actions, rollback, backup restore, and the live monthly bill are unverified.
- The cost is an estimate, not a quote. ALB, Fargate, RDS, public IPv4, DNS,
  secrets, storage, logs, and retained snapshots/images can charge at low traffic.
- One ECS task and Single-AZ RDS are not highly available. Multiple tasks require
  shared sessions and distributed/edge throttling first.
- Database TLS uses `sslmode=require` in the prepared task. Certificate identity
  verification needs an RDS CA bundle and a tested `verify-full` rollout before
  a stronger claim.
- Backend/container software-composition analysis and formal penetration testing
  remain launch recommendations, not completed certification.
- Live-provider behavior is unverified and remains opt-in/cost-capped. The public
  design defaults to mock AI and does not expose a paid provider credential.
- Public report/audit/data deletion policy, formal RPO/RTO, and production data
  retention approval remain owner/legal decisions. No automatic deletion runs.

## Stage B approval boundary

Do not run Stage B until every approval item in
[deployment-checklist.md](deployment-checklist.md) is accepted. After approval,
review a saved Terraform plan, provision only that plan, bootstrap synthetic
data, immediately disable registration, and run the full HTTPS, authorization,
idempotency, logs, alarms, budget, and restore checklist. Add a live URL only
after the resulting evidence is recorded.
