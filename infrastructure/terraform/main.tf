# TrendStream GCP Infrastructure
# Terraform configuration for deploying on Google Cloud Platform

terraform {
  required_version = ">= 1.5.0"
  
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
  
  # Uncomment for remote state storage
  # backend "gcs" {
  #   bucket = "trendstream-terraform-state"
  #   prefix = "terraform/state"
  # }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

# Enable required APIs
resource "google_project_service" "apis" {
  for_each = toset([
    "container.googleapis.com",      # GKE
    "bigquery.googleapis.com",       # BigQuery
    "compute.googleapis.com",        # Compute Engine
    "iam.googleapis.com",            # IAM
    "cloudresourcemanager.googleapis.com",
  ])
  
  service            = each.value
  disable_on_destroy = false
}
