# AWS Terraform deployment

This directory is Stage A infrastructure preparation. Running `terraform
apply` creates billable and publicly reachable resources and is prohibited
until the repository owner explicitly approves Stage B.

The stack provisions a private S3 frontend origin, CloudFront, ACM/Route 53,
an HTTPS ALB restricted to CloudFront origin addresses plus a secret origin
header, one ECS/Fargate backend task, private Single-AZ RDS PostgreSQL, ECR,
CloudWatch logs/alarms, SNS, and an AWS Budget. It intentionally creates no NAT
Gateway, broker, Redis cluster, public database, or paid model configuration.

Prerequisites and the bootstrap/deploy/destroy procedures are documented in
[`docs/deployment-checklist.md`](../../../docs/deployment-checklist.md). The
remote S3 state bucket must exist before initialization and must have
versioning, encryption, public-access blocking, and restricted IAM access.

Safe local validation performs no AWS API call:

```bash
terraform fmt -check -recursive infra/aws/terraform
terraform -chdir=infra/aws/terraform init -backend=false
terraform -chdir=infra/aws/terraform validate
```

Never commit `terraform.tfvars`, state files, plans, credentials, domain
ownership details, or generated secrets.
