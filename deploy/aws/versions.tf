terraform {
  required_version = ">= 1.13.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.72.0"
    }
  }

  # Local state is intentional for this single-operator starting point. Keep
  # terraform.tfstate and its backups safe: they are needed to manage imports.
  # Configure an encrypted remote backend before sharing management with others.
}

provider "aws" {
  profile             = var.aws_profile
  region              = var.aws_region
  allowed_account_ids = [var.aws_account_id]
}
