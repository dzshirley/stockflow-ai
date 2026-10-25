# Phase 9 Deployment Cost and Safety Gate

Estimate date: 2026-10-24. Region: US East (N. Virginia). Currency: USD before
tax. Prices change; confirm every line in the AWS Pricing Calculator immediately
before Stage B approval.

## Assumptions

- 730 hours/month, one Fargate task with 1 vCPU and 2 GB memory.
- One low-traffic ALB and approximately one LCU.
- One Single-AZ `db.t4g.micro`, 20 GB gp3, seven-day backup retention.
- One ECS task public IPv4 and the ALB's typical two public IPv4 addresses.
- Less than 1 GB ECR storage, 1 GB application logs, 1 GB frontend assets, 20 GB
  CloudFront transfer, and 100,000 DNS queries.
- Mock AI provider and no WAF, NAT Gateway, Redis, queue, or paid support plan.

## Recommended architecture estimate

| Resource | Assumption | Estimated monthly cost |
|---|---|---:|
| ECS/Fargate Linux x86 | 1 vCPU + 2 GB continuously | $36.04 |
| Application Load Balancer | $0.0225/hour + about 1 LCU | $22.27 |
| Public IPv4 | approximately three addresses at $0.005/hour | $10.95 |
| RDS PostgreSQL | `db.t4g.micro` Single-AZ | $11.68 |
| RDS gp3 | 20 GB at approximately $0.115/GB-month | $2.30 |
| Secrets Manager | one RDS-managed credential | $0.40 |
| ECR | less than 1 GB at $0.10/GB-month | $0.10 |
| Route 53 | one hosted zone plus low query volume | about $0.54 |
| S3 + CloudFront | low demo storage/traffic, normally within allowances | $0–1 |
| CloudWatch/SNS/Budgets | low logs and alarm count; allowance dependent | $0–5 |
| **Expected baseline** | excluding domain registration, tax, and AI | **about $88–95/month** |

CloudFront currently includes 1 TB transfer and 10 million requests per month
in its free usage tier. Free-tier eligibility and credits are account-specific
and must not be treated as a guaranteed architecture price.

Official pricing references:

- [AWS Fargate pricing](https://aws.amazon.com/fargate/pricing/)
- [Elastic Load Balancing pricing](https://aws.amazon.com/elasticloadbalancing/pricing/)
- [RDS for PostgreSQL pricing](https://aws.amazon.com/rds/postgresql/pricing/)
- [Amazon VPC and public IPv4 pricing](https://aws.amazon.com/vpc/pricing/)
- [CloudFront pricing and free usage](https://aws.amazon.com/cloudfront/pricing/)
- [S3 pricing](https://aws.amazon.com/s3/pricing/)
- [ECR pricing](https://aws.amazon.com/ecr/pricing/)
- [Secrets Manager pricing](https://aws.amazon.com/secrets-manager/pricing/)
- [CloudWatch pricing](https://aws.amazon.com/cloudwatch/pricing/)
- [Route 53 pricing](https://aws.amazon.com/route53/pricing/)
- [ACM pricing](https://aws.amazon.com/certificate-manager/pricing/)

## Lower-cost alternatives

AWS lists the Lightsail 2 GB Linux bundle at $12/month and its smallest
encrypted managed database plan at $30/month. With DNS, snapshots, and modest
logs, budget approximately $42–50/month. A single-host $12–16 design is cheaper
but co-locates PostgreSQL and the application, so it is not the recommended
write-capable public demo. See [Lightsail pricing](https://aws.amazon.com/lightsail/pricing/).

## Charges that continue at little or no traffic

- Fargate task CPU/memory, ALB hours, ALB LCUs, public IPv4 addresses, RDS
  instance/storage, Route 53 hosted zones, Secrets Manager secrets, retained ECR
  images, S3 versions, snapshots, and CloudWatch log retention.
- RDS burst CPU credits can add cost if a burstable instance sustains CPU above
  baseline.
- Stopping RDS is temporary; AWS restarts a stopped database after seven days.
- A NAT Gateway would add roughly $32.85/month before data processing, which is
  why this design does not create one.
- Domain registration is an annual, non-refundable charge and is excluded.

## AI cost boundary

Production defaults to `AI_PROVIDER=mock`, so model cost is $0. Switching to a
live provider requires separate approval, a server-side key, the existing
request/tool/token limits, versioned estimated-cost configuration, and an abuse
budget. Provider-reported usage is not the same as the final bill. Never publish
a demo credential with unrestricted paid-model access.

## Budget and safety controls

Terraform creates a $100 monthly budget by default with 50%, 80%, and 100%
notifications. The operator must confirm the SNS email subscription and budget
emails. Recommended account controls also include MFA on the root user, no root
access keys, least-privilege OIDC deployment, Cost Explorer review, and an
account-level unexpected-spend alert.

## Safe stop and deletion

For a temporary stop, set the ECS service desired count to zero and stop RDS,
understanding that ALB, storage, IP, logs, DNS, and the database still accrue
charges. For full teardown:

1. Export evidence and test a database restore if retention is required.
2. Disable CloudFront and wait for deployment.
3. Set ALB and RDS deletion protection to false in a reviewed Terraform plan.
4. Apply destruction and require the final RDS snapshot.
5. Delete retained ECR images, noncurrent S3 versions, log groups, snapshots,
   secrets, state objects, and DNS only after confirming retention requirements.
6. Check Cost Explorer and Public IP Insights for orphaned billable resources.

No stop or deletion action is automated by the deployment workflow.
