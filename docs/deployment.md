# Deployment and Operations

## Local development

The supported integrated path is `docker compose up --build`. Compose starts PostgreSQL first, waits for readiness, then starts the API and frontend. Flyway applies migrations during API startup.

Host-native development requires Java 21, Maven through `./mvnw`, Node compatible with Vite, npm, and a reachable PostgreSQL instance. See the root README for exact commands.

## Production direction

No cloud resources are provisioned in Phase 1. After explicit approval, use a cost-conscious single-region AWS design:

- CloudFront plus S3 for immutable frontend assets, or the included Nginx container for a simpler first deployment.
- One small ECS Fargate backend service behind an HTTPS Application Load Balancer.
- Small encrypted RDS PostgreSQL with automated backups and private networking.
- RabbitMQ only after async workflows exist; compare Amazon MQ cost with a small managed alternative.
- ACM certificates, Route 53 DNS, Secrets Manager/SSM, CloudWatch structured logs/alarms, and least-privilege task roles.
- Rolling deployment with `/api/health` liveness plus a future dependency-aware readiness endpoint.

## CI/CD stages

1. Compile, unit/component test, lint, type-check, and build on pull requests.
2. Add Testcontainers and Playwright jobs as those phases land.
3. Build immutable images, scan dependencies/images, and publish by commit SHA.
4. Require approval for production, run migrations as a controlled task, deploy, verify health, and retain rollback image/database procedures.

## Secrets and configuration

`.env.example` contains development-only names and values. Production secrets must never be baked into frontend code, Git, images, or logs. The backend receives secrets at runtime from the deployment secret store.
