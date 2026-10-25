terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Stage B must supply the bucket at init time:
  # terraform init -backend-config="bucket=YOUR_STATE_BUCKET"
  backend "s3" {
    key          = "stockflow/production/terraform.tfstate"
    region       = "us-east-1"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "StockFlow AI"
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}
