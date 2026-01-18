# GKE Cluster for Apache Flink

resource "google_container_cluster" "flink" {
  name     = "trendstream-flink-${var.environment}"
  location = var.region
  
  # Use Autopilot for cost efficiency
  enable_autopilot = true
  
  # Network configuration
  network    = "default"
  subnetwork = "default"
  
  # Release channel for automatic updates
  release_channel {
    channel = "REGULAR"
  }
  
  # Workload Identity for secure GCP API access
  workload_identity_config {
    workload_pool = "${var.project_id}.svc.id.goog"
  }
  
  # Deletion protection (disable for dev)
  deletion_protection = var.environment == "prod" ? true : false
  
  depends_on = [google_project_service.apis]
}

# Service Account for Flink workloads
resource "google_service_account" "flink" {
  account_id   = "trendstream-flink"
  display_name = "TrendStream Flink Service Account"
}

# Grant BigQuery access to Flink service account
resource "google_project_iam_member" "flink_bigquery" {
  project = var.project_id
  role    = "roles/bigquery.dataEditor"
  member  = "serviceAccount:${google_service_account.flink.email}"
}

resource "google_project_iam_member" "flink_bigquery_user" {
  project = var.project_id
  role    = "roles/bigquery.user"
  member  = "serviceAccount:${google_service_account.flink.email}"
}

# Grant GCS access for checkpoints
resource "google_project_iam_member" "flink_storage" {
  project = var.project_id
  role    = "roles/storage.objectAdmin"
  member  = "serviceAccount:${google_service_account.flink.email}"
}

# Workload Identity binding
resource "google_service_account_iam_member" "flink_workload_identity" {
  service_account_id = google_service_account.flink.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.project_id}.svc.id.goog[flink/flink-sa]"
  
  depends_on = [google_container_cluster.flink]
}

# Output cluster info
output "gke_cluster_name" {
  value = google_container_cluster.flink.name
}

output "gke_cluster_endpoint" {
  value     = google_container_cluster.flink.endpoint
  sensitive = true
}

output "flink_service_account" {
  value = google_service_account.flink.email
}
