# =============================================================================
# Google Cloud Storage - Flink Data Lake
# =============================================================================
#
# This bucket stores:
# - Flink checkpoints (state snapshots for recovery)
# - Flink savepoints (manual snapshots for upgrades)
# - Iceberg table data (from PySpark batch jobs)
# - Dead Letter Queue exports (for debugging)
#
# =============================================================================

resource "google_storage_bucket" "flink_data" {
  name          = "${var.project_id}-data"
  location      = var.region
  force_destroy = var.environment != "prod"
  
  # Prevent public access
  public_access_prevention = "enforced"
  
  # Enable versioning for data protection
  versioning {
    enabled = true
  }

  # Lifecycle rules to manage storage costs
  lifecycle_rule {
    condition {
      age = 7
      matches_prefix = ["checkpoints/"]
    }
    action {
      type = "Delete"
    }
  }

  lifecycle_rule {
    condition {
      age = 30
      matches_prefix = ["dlq/"]
    }
    action {
      type = "Delete"
    }
  }

  lifecycle_rule {
    condition {
      age = 90
      matches_prefix = ["savepoints/"]
    }
    action {
      type = "Delete"
    }
  }

  # Move old Iceberg data to Nearline after 30 days
  lifecycle_rule {
    condition {
      age = 30
      matches_prefix = ["iceberg/"]
    }
    action {
      type          = "SetStorageClass"
      storage_class = "NEARLINE"
    }
  }

  labels = {
    environment = var.environment
    project     = "streamforge"
    managed_by  = "terraform"
  }
}

# Checkpoint directory structure
resource "google_storage_bucket_object" "checkpoint_folder" {
  name    = "checkpoints/"
  content = " "
  bucket  = google_storage_bucket.flink_data.name
}

resource "google_storage_bucket_object" "savepoint_folder" {
  name    = "savepoints/"
  content = " "
  bucket  = google_storage_bucket.flink_data.name
}

resource "google_storage_bucket_object" "dlq_folder" {
  name    = "dlq/"
  content = " "
  bucket  = google_storage_bucket.flink_data.name
}

resource "google_storage_bucket_object" "iceberg_folder" {
  name    = "iceberg/"
  content = " "
  bucket  = google_storage_bucket.flink_data.name
}

# Output the bucket name for reference
output "flink_data_bucket" {
  value       = google_storage_bucket.flink_data.name
  description = "GCS bucket for Flink checkpoints, savepoints, and Iceberg data"
}

output "checkpoint_path" {
  value       = "gs://${google_storage_bucket.flink_data.name}/checkpoints"
  description = "GCS path for Flink checkpoints"
}

output "savepoint_path" {
  value       = "gs://${google_storage_bucket.flink_data.name}/savepoints"
  description = "GCS path for Flink savepoints"
}
