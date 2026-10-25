# Phase 9 Deployment and Approval Checklist

This checklist separates preparation from authorization. Every Stage B box must
be reviewed by the repository owner before any billable or public action.

## Approval gate

- [ ] Approve the selected architecture and the estimated $88–95/month baseline.
- [ ] Approve the AWS account, `us-east-1`, domain/hostnames, and public exposure.
- [ ] Approve a monthly budget and the alert recipient.
- [ ] Approve mock-only AI for the public demo; live AI requires a separate cap.
- [ ] Confirm synthetic-data-only policy and controlled demo credentials.
- [ ] Confirm RDS Single-AZ availability trade-off and seven-day backup policy.
- [ ] Confirm the retention/deletion owner for reports, audits, logs, snapshots,
  S3 versions, and Terraform state.

## Account and state bootstrap

- [ ] Enable root MFA, remove root access keys, and choose a least-privilege admin.
- [ ] Create/verify the GitHub Actions OIDC provider.
- [ ] Create a private, encrypted, versioned S3 Terraform-state bucket with
  public access blocked; record its name as a protected GitHub variable.
- [ ] Configure the protected GitHub `production` environment with required
  reviewers and deployment branch/tag restrictions.
- [ ] Confirm no long-lived AWS access key is stored in GitHub.
- [ ] Confirm Route 53 hosted zone ownership; do not purchase a domain implicitly.

## Infrastructure plan

Run only after approval and authenticated AWS CLI setup:

```bash
terraform -chdir=infra/aws/terraform init \
  -backend-config="bucket=APPROVED_STATE_BUCKET"
terraform -chdir=infra/aws/terraform plan -out=phase9.tfplan
terraform -chdir=infra/aws/terraform show phase9.tfplan
```

- [ ] Review every create/change/delete in the saved plan.
- [ ] Verify no NAT Gateway, public RDS address, Redis, broker, paid AI key, or
  unexpected WAF/service appears.
- [ ] Verify deletion protection, backups, TLS, security groups, log retention,
  budget notifications, and immutable image tags.
- [ ] Decide and test PostgreSQL server identity verification with the current
  RDS CA bundle (`verify-full`); Stage A only guarantees encrypted
  `sslmode=require` transport.
- [ ] Create the ECR repository first as the documented bootstrap exception,
  build/push immutable backend and seed images, then apply the reviewed full plan.
- [ ] Confirm the SNS alarm subscription email.

## Controlled demo bootstrap

1. Deploy once with `registration_enabled=true` only during the supervised
   bootstrap window.
2. Set `DEMO_BASE_URL`, `DEMO_EMAIL`, and a generated password in the operator's
   shell—not a file—and run `scripts/demo/bootstrap-account.sh`.
3. Record returned user/store UUIDs in the approved secret/operations record.
4. Immediately apply `registration_enabled=false` and verify both the UI and
   `POST /api/auth/register` reject public registration.
5. Run the one-off `demo-seed` ECS task with UUID environment overrides. Its
   image contains only the checked-in `DEMO-*` SQL and exits after completion.
6. Run `scripts/demo/prepare-workflows.sh` to create deterministic mock AI,
   report, and unapproved proposal examples.
7. Store credentials in a password manager, share privately, rotate after demos,
   and never publish them in README, screenshots, workflow logs, or issues.

## Post-deployment verification

- [ ] Public frontend loads over HTTPS and HTTP redirects to HTTPS.
- [ ] CSP, HSTS, frame, referrer, content-type, and permissions headers exist.
- [ ] `/api/health` succeeds through CloudFront; direct origin access is denied.
- [ ] ALB target and Actuator readiness are healthy; health details are hidden.
- [ ] Flyway reports V1–V9 successful and Hibernate schema validation passes.
- [ ] PostgreSQL is private and requires TLS; security groups match the design.
- [ ] Controlled login/logout and CSRF-protected mutation work.
- [ ] Registration is disabled and invalid logins are throttled.
- [ ] Products, inventory, paid orders, analytics, and synthetic trend data render.
- [ ] Mock AI Data Analyst returns deterministic backend-owned metrics.
- [ ] Business report persists and retains evidence references.
- [ ] Proposal generation works; only ADMIN can approve/execute.
- [ ] Duplicate receipt execution changes inventory exactly once.
- [ ] Cross-store IDs remain inaccessible.
- [ ] CloudWatch logs exclude secrets/payloads and preserve request IDs.
- [ ] ALB/RDS/app alarms and Budget notifications reach the operator.
- [ ] ECR scanning result has no untriaged Critical/High finding.
- [ ] No customer data, private document, real payment, or paid AI credential exists.

After explicit approval, run Playwright with `E2E_BASE_URL=https://...` only
against an isolated test tenant or a resettable environment. Current E2E creates
accounts and therefore must not run after public registration is disabled unless
the test setup is adapted to a pre-provisioned isolated account.

## Release and rollback

- [ ] Deploy a reviewed full commit SHA through the manual workflow.
- [ ] Require successful reusable release verification and environment approval.
- [ ] Verify ECS service stability and public health before announcing a release.
- [ ] For application rollback, redeploy the previous immutable task revision and
  previous S3 object versions, then invalidate CloudFront.
- [ ] Never assume application rollback reverses Flyway. Use forward repair or an
  explicitly tested snapshot restore with accepted data-loss boundaries.

No live URL belongs in project documentation until every applicable check above
has recorded evidence.
