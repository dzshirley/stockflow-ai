# Deployment and Operations

## Local development

The supported integrated path is `docker compose up --build`. Compose starts PostgreSQL first, waits for readiness, then starts the API and frontend. Flyway applies migrations during API startup.

Host-native development requires Java 21, Maven through `./mvnw`, Node compatible with Vite, pnpm, and a reachable PostgreSQL instance. See the root README for exact commands.

## Prepared production deployment

Phase 9 Stage A includes validated Terraform under `infra/aws/terraform`, but no
AWS resource, domain, certificate, public URL, or paid model has been created.
The recommended single-region design uses:

- private S3 plus CloudFront for immutable frontend assets and same-origin API
  routing;
- one ECS/Fargate backend task behind an HTTPS ALB restricted to CloudFront;
- encrypted, non-public Single-AZ RDS PostgreSQL with seven-day backups;
- an RDS-managed Secrets Manager credential, ECR immutable images, Route 53,
  ACM, CloudWatch Logs/alarms, SNS, and AWS Budgets;
- no NAT Gateway, broker, Redis, WAF, public database, or live paid AI by
  default.

This architecture is estimated at $88–95/month before tax, domain registration,
and paid AI. See [cloud architecture](cloud-architecture.md),
[cost and safety gate](deployment-cost-estimate.md), and the
[approval checklist](deployment-checklist.md). Terraform `plan` and every apply
or public action remain Stage B and require explicit approval.

## Production runtime contract

Activate `SPRING_PROFILES_ACTIVE=prod`. Supply PostgreSQL host, port, database,
user, and password only through runtime configuration; require database TLS and
`SESSION_COOKIE_SECURE=true`. Public registration defaults disabled. The profile
bounds Hikari connections and acquisition/lifetime/validation timeouts, honors
forwarded HTTPS headers, hides health details, disables Flyway clean, and uses
graceful shutdown with a 30-second lifecycle bound.

The browser and API share one CloudFront origin, so no permissive production
CORS rule is needed. Hashed frontend assets receive one-year immutable caching;
`index.html` is never cached and extensionless routes rewrite to the SPA entry.
CloudFront applies CSP, HSTS, frame, referrer, content-type, and permissions
headers. PostgreSQL has no public address or inbound rule outside the backend
security group.

## CI/CD stages

1. Compile and run Java 21 unit/MVC/evaluation and PostgreSQL Testcontainers
   checks with a 20-minute bound.
2. Run pinned Node/pnpm type-check, zero-warning lint, Vitest, production build,
   and bundle budgets with a 15-minute bound.
3. Build the integrated Compose stack, require Actuator readiness, and run all
   Chromium Playwright workflows with a 25-minute bound.
4. Scan common committed secret formats, audit production frontend dependencies,
   and use weekly Dependabot updates for Maven, npm, Docker, and Actions.
5. A manually dispatched release workflow requires a full reviewed commit SHA,
   typed `DEPLOY` confirmation, a protected GitHub environment, and short-lived
   OIDC credentials. It builds immutable images, updates S3 cache metadata,
   waits for ECS health/circuit-breaker rollback, and verifies public HTTPS
   health. The workflow is prepared but has not run against AWS.

## Secrets and configuration

`.env.example` contains development-only names and values. Production secrets must never be baked into frontend code, Git, images, or logs. The backend receives secrets at runtime from the deployment secret store.

`SESSION_COOKIE_SECURE=true` is mandatory behind HTTPS. Login throttling and
sessions are currently application-local; horizontal scaling requires an edge
or shared rate-limit policy and sticky or shared session design. The 1 MiB body
and 16 KiB header defaults are defense-in-depth and should change only for a
documented API requirement.

## Migrations, backups, and recovery

The single backend task runs Flyway before accepting traffic. Flyway's schema
history lock prevents concurrent runners from applying the same migration, but
all production changes must still be forward-compatible; application rollback
does not undo SQL. Take and verify a manual snapshot before a risky migration.
Restore drills use a separate private RDS instance, validate Flyway and target
queries, then remove that isolated instance after evidence is recorded.

Operational response, alarm thresholds, outbox limitations, rotation, and
teardown procedures are in [operations-runbook.md](operations-runbook.md). The
transactional outbox has no relay or consumer, so delivery is not monitored or
claimed; it currently guarantees only atomic durable recording.
