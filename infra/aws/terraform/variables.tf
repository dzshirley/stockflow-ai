variable "aws_region" {
  description = "Deployment region. us-east-1 keeps the CloudFront ACM certificate in the required region."
  type        = string
  default     = "us-east-1"

  validation {
    condition     = var.aws_region == "us-east-1"
    error_message = "This portfolio deployment is intentionally scoped to us-east-1."
  }
}

variable "environment" {
  description = "Environment tag and resource-name suffix."
  type        = string
  default     = "production"
}

variable "project_name" {
  description = "Lowercase resource prefix."
  type        = string
  default     = "stockflow-ai"

  validation {
    condition     = can(regex("^[a-z0-9-]{3,24}$", var.project_name))
    error_message = "project_name must contain 3-24 lowercase letters, digits, or hyphens."
  }
}

variable "domain_name" {
  description = "Existing Route 53 public-zone domain, for example example.com. No domain is purchased by this configuration."
  type        = string
}

variable "hosted_zone_id" {
  description = "Existing Route 53 public hosted-zone ID for domain_name."
  type        = string
}

variable "app_subdomain" {
  description = "Public CloudFront hostname prefix."
  type        = string
  default     = "stockflow"
}

variable "origin_subdomain" {
  description = "CloudFront-to-ALB TLS origin hostname prefix."
  type        = string
  default     = "stockflow-origin"
}

variable "backend_image_tag" {
  description = "Immutable backend image tag, normally the full Git commit SHA."
  type        = string

  validation {
    condition     = length(trimspace(var.backend_image_tag)) >= 7 && var.backend_image_tag != "latest"
    error_message = "Use an immutable image tag such as a Git commit SHA, never latest."
  }
}

variable "demo_seed_image_tag" {
  description = "Immutable one-off seed image tag built from scripts/demo/Dockerfile."
  type        = string
  default     = "demo-seed-v1"
}

variable "registration_enabled" {
  description = "Temporary Stage B bootstrap switch. Return to false immediately after creating the controlled demo account."
  type        = bool
  default     = false
}

variable "alarm_email" {
  description = "Email endpoint for budget and operational alarm confirmation."
  type        = string
  sensitive   = true
}

variable "monthly_budget_usd" {
  description = "Monthly cost budget. Alerts fire at 50, 80, and 100 percent."
  type        = number
  default     = 100
}

variable "db_deletion_protection" {
  description = "Must remain true except during an explicitly approved teardown."
  type        = bool
  default     = true
}

variable "log_retention_days" {
  description = "CloudWatch application-log retention."
  type        = number
  default     = 14
}

variable "github_oidc_provider_arn" {
  description = "ARN of the account's existing token.actions.githubusercontent.com OIDC provider."
  type        = string
}

variable "github_repository" {
  description = "GitHub owner/repository allowed to deploy."
  type        = string
  default     = "dzshirley/stockflow-ai"
}

variable "github_environment" {
  description = "Protected GitHub environment required by the deployment workflow."
  type        = string
  default     = "production"
}
