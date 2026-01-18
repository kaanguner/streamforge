# BigQuery Analytics Dataset and Tables

resource "google_bigquery_dataset" "analytics" {
  dataset_id    = "trendstream_analytics"
  friendly_name = "TrendStream Analytics"
  description   = "Real-time analytics data from TrendStream streaming pipeline"
  location      = "EU"
  
  # Delete contents on destroy (for non-prod)
  delete_contents_on_destroy = var.environment != "prod"
  
  labels = {
    environment = var.environment
    project     = "trendstream"
  }
}

# Real-time Revenue Table (streaming inserts from Flink)
resource "google_bigquery_table" "real_time_revenue" {
  dataset_id          = google_bigquery_dataset.analytics.dataset_id
  table_id            = "real_time_revenue"
  deletion_protection = var.environment == "prod"
  
  description = "Per-minute revenue metrics computed by Flink RevenueCalculator"
  
  time_partitioning {
    type  = "HOUR"
    field = "window_end_timestamp"
  }
  
  clustering = ["region"]
  
  schema = jsonencode([
    {
      name = "window_start_timestamp"
      type = "TIMESTAMP"
      mode = "REQUIRED"
      description = "Start of aggregation window"
    },
    {
      name = "window_end_timestamp"
      type = "TIMESTAMP"
      mode = "REQUIRED"
      description = "End of aggregation window"
    },
    {
      name = "gmv"
      type = "FLOAT64"
      mode = "REQUIRED"
      description = "Gross Merchandise Value in TRY"
    },
    {
      name = "order_count"
      type = "INT64"
      mode = "REQUIRED"
      description = "Number of orders in window"
    },
    {
      name = "average_order_value"
      type = "FLOAT64"
      mode = "NULLABLE"
      description = "AOV in TRY"
    },
    {
      name = "unique_users"
      type = "INT64"
      mode = "NULLABLE"
      description = "Unique ordering users"
    },
    {
      name = "first_time_orders"
      type = "INT64"
      mode = "NULLABLE"
      description = "First-time buyer orders"
    },
    {
      name = "region"
      type = "STRING"
      mode = "NULLABLE"
      description = "Geographic region"
    },
    {
      name = "category"
      type = "STRING"
      mode = "NULLABLE"
      description = "Product category"
    },
    {
      name = "category_revenue"
      type = "FLOAT64"
      mode = "NULLABLE"
      description = "Revenue for this category"
    },
    {
      name = "ingestion_timestamp"
      type = "TIMESTAMP"
      mode = "REQUIRED"
      description = "When this record was inserted"
    }
  ])
  
  labels = {
    environment = var.environment
  }
}

# User Sessions Table
resource "google_bigquery_table" "user_sessions" {
  dataset_id          = google_bigquery_dataset.analytics.dataset_id
  table_id            = "user_sessions"
  deletion_protection = var.environment == "prod"
  
  description = "Aggregated user session data from SessionAggregator"
  
  time_partitioning {
    type  = "DAY"
    field = "session_end_timestamp"
  }
  
  clustering = ["device_type", "geo_region"]
  
  schema = jsonencode([
    {
      name = "user_id"
      type = "STRING"
      mode = "REQUIRED"
    },
    {
      name = "session_id"
      type = "STRING"
      mode = "REQUIRED"
    },
    {
      name = "session_start_timestamp"
      type = "TIMESTAMP"
      mode = "REQUIRED"
    },
    {
      name = "session_end_timestamp"
      type = "TIMESTAMP"
      mode = "REQUIRED"
    },
    {
      name = "duration_seconds"
      type = "INT64"
      mode = "NULLABLE"
    },
    {
      name = "page_views"
      type = "INT64"
      mode = "NULLABLE"
    },
    {
      name = "product_views"
      type = "INT64"
      mode = "NULLABLE"
    },
    {
      name = "add_to_cart_count"
      type = "INT64"
      mode = "NULLABLE"
    },
    {
      name = "purchase_count"
      type = "INT64"
      mode = "NULLABLE"
    },
    {
      name = "total_revenue"
      type = "FLOAT64"
      mode = "NULLABLE"
    },
    {
      name = "device_type"
      type = "STRING"
      mode = "NULLABLE"
    },
    {
      name = "geo_region"
      type = "STRING"
      mode = "NULLABLE"
    },
    {
      name = "primary_category"
      type = "STRING"
      mode = "NULLABLE"
    },
    {
      name = "conversion_rate"
      type = "FLOAT64"
      mode = "NULLABLE"
    }
  ])
}

# Fraud Alerts Table
resource "google_bigquery_table" "fraud_alerts" {
  dataset_id          = google_bigquery_dataset.analytics.dataset_id
  table_id            = "fraud_alerts"
  deletion_protection = var.environment == "prod"
  
  description = "Fraud detection alerts from FraudDetector"
  
  time_partitioning {
    type  = "DAY"
    field = "detected_at_timestamp"
  }
  
  schema = jsonencode([
    {
      name = "alert_id"
      type = "STRING"
      mode = "REQUIRED"
    },
    {
      name = "user_id"
      type = "STRING"
      mode = "REQUIRED"
    },
    {
      name = "order_id"
      type = "STRING"
      mode = "NULLABLE"
    },
    {
      name = "alert_type"
      type = "STRING"
      mode = "REQUIRED"
      description = "RAPID_ORDERS, LOCATION_ANOMALY, HIGH_VALUE_FIRST_ORDER, SUSPICIOUS_PATTERN"
    },
    {
      name = "risk_score"
      type = "FLOAT64"
      mode = "REQUIRED"
    },
    {
      name = "description"
      type = "STRING"
      mode = "NULLABLE"
    },
    {
      name = "detected_at_timestamp"
      type = "TIMESTAMP"
      mode = "REQUIRED"
    },
    {
      name = "related_event_ids"
      type = "STRING"
      mode = "REPEATED"
    }
  ])
}

# Outputs
output "bigquery_dataset" {
  value = google_bigquery_dataset.analytics.dataset_id
}

output "bigquery_tables" {
  value = [
    google_bigquery_table.real_time_revenue.table_id,
    google_bigquery_table.user_sessions.table_id,
    google_bigquery_table.fraud_alerts.table_id
  ]
}
