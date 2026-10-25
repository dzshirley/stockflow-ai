# StockFlow AI Operations Runbook

This runbook describes the prepared AWS design. It is not evidence of a live
deployment.

## First response

1. Confirm CloudFront reachability and `GET /api/health` over the public HTTPS
   origin.
2. Check the ALB healthy-target count and ECS service deployment/events.
3. Correlate the affected request using `X-Request-ID` in CloudWatch Logs.
4. Check RDS availability, CPU, connections, storage, and PostgreSQL logs.
5. Determine whether impact is core commerce, AI-only, or deployment-wide.
6. Preserve logs and timestamps before restarting or rolling back.

Do not log or paste passwords, session cookies, CSRF tokens, prompts, business
documents, database URLs, or provider responses into tickets.

## Alert actions

| Signal | Initial action | Escalation |
|---|---|---|
| Unhealthy ALB targets | Inspect ECS stopped reason and startup/Flyway logs; keep last healthy revision | Roll back task revision if the new image fails readiness |
| ALB target 5xx | Group safe error codes/request IDs; inspect DB/provider dependency | Roll back only when correlated with release; AI-only failure does not justify commerce shutdown |
| API p95 > 1 second | Check task CPU/memory, RDS CPU/connections, and slow queries | Scale task only after session design; resize DB or tune verified query bottleneck |
| RDS CPU > 80% | Inspect query mix, locks, and burst credits | Reduce load, tune query/index, or resize; do not add speculative cache |
| RDS connections > 80 sustained | Compare the eight-connection task pool with DB sessions and abandoned clients | Drain/restart only after diagnosis; resize or revise the pool before adding tasks |
| RDS free storage < 2 GiB | Find table/log growth and autoscaling state | Increase storage and review retention; never delete audits ad hoc |
| AI failure count | Confirm mock/live mode and provider status | Disable live provider and retain core commerce/direct analytics |
| Business report failures | Inspect safe code, provider health, and the persisted FAILED report | Correct the dependency/input; never fabricate or manually mark a report complete |
| Proposal execution failures | Inspect attempt, proposal events, inventory adjustment, and outbox by IDs | Reconcile the committed effect before any explicit retry |
| Expired report/proposal recovery | Inspect lease owner, DB health, and preceding task interruption | Reconcile through bounded worker; never repeat side effects blindly |
| Budget threshold | Open Cost Explorer by service/usage type | Stop nonessential task, stop RDS temporarily, or execute approved teardown |

## Deployment failure

ECS circuit-breaker rollback should retain the last healthy task. Inspect the
new task's stopped reason, RDS connectivity, secret permissions, Flyway output,
and readiness response. Do not bypass readiness or disable migrations merely to
make deployment green.

If a forward migration applied before startup failed, redeploying old code is
safe only when the migration was backward compatible. Otherwise prepare a
forward repair. Snapshot restore creates a new database and can discard newer
writes; it requires explicit incident-owner approval.

## Database backup and restore drill

- Automated backups: seven days in the prepared Terraform configuration.
- Before risky migration: create a manual snapshot and wait for completion.
- Quarterly or before launch: restore a snapshot into an isolated private DB,
  connect with a temporary restricted task, run Flyway validation and targeted
  smoke queries, then delete the restored instance.
- Record recovery-point and recovery-time observations. A snapshot existing is
  not proof that restore works.

## AI and operational failures

AI transport retries are bounded to two attempts. Malformed responses are not
retried. Switch back to `AI_PROVIDER=mock` when a live provider is unhealthy or
cost-limited; product, inventory, orders, and direct analytics remain available.
Successful AI logs include provider/model, latency, tool count, token counts,
pricing version, and estimated cost only; they exclude prompts and responses.
The PostgreSQL telemetry rows are the durable operational source, while an
estimate must never be presented as an actual provider bill.

Restock execution requires approved state, exact version, current evidence and
inventory, ADMIN authorization, physical-receipt confirmation, and command
idempotency. On ambiguous execution, inspect the unique inventory adjustment,
attempt, proposal event, and outbox row before any retry. Recovery finalizes a
committed result or returns an expired claim for explicit revalidation; it never
blindly repeats the inventory increment.

## Outbox

The outbox has no external consumer or relay. Its guarantee ends at atomic,
durable PostgreSQL persistence. There is no delivery-failure alarm because
nothing is delivered. When a named consumer exists, add a bounded relay,
attempt/dead-letter visibility, and consumer deduplication before claiming
at-least-once delivery.

## Access and secrets

- GitHub assumes the deployment role with short-lived OIDC credentials.
- The ECS execution role may read only the database secret needed at startup.
- Rotate controlled demo credentials after public walkthroughs.
- Rotate a database secret with a coordinated ECS deployment and connection-pool
  drain. Test rotation in staging first.
- Audit IAM, public IPv4, security-group, Route 53, and Secrets Manager inventory
  during every cost review.

## Teardown

Follow [deployment-cost-estimate.md](deployment-cost-estimate.md) and
[deployment-checklist.md](deployment-checklist.md). Deletion protection must not
be disabled casually. Confirm the final snapshot, artifact/audit retention, DNS,
S3 versions, ECR images, log groups, public IPs, and Terraform state explicitly.
