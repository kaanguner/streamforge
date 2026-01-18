"""
TrendStream Configuration
Centralized configuration for the event producer.
"""
import os
from dataclasses import dataclass
from typing import Optional


@dataclass
class KafkaConfig:
    """Kafka connection configuration."""
    bootstrap_servers: str = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:29092")
    schema_registry_url: str = os.getenv("SCHEMA_REGISTRY_URL", "http://localhost:8081")
    
    # Topic names
    clickstream_topic: str = "clickstream.events"
    orders_topic: str = "transactions.orders"
    payments_topic: str = "transactions.payments"
    inventory_topic: str = "inventory.updates"
    
    # Producer settings
    acks: str = "all"  # Wait for all replicas
    retries: int = 3
    linger_ms: int = 5  # Batch for 5ms
    batch_size: int = 16384  # 16KB batches


@dataclass
class SimulatorConfig:
    """Event simulator configuration."""
    # Event generation
    events_per_second: int = int(os.getenv("EVENTS_PER_SECOND", "100"))
    total_events: Optional[int] = None  # None = infinite
    
    # User simulation
    num_users: int = int(os.getenv("NUM_USERS", "1000"))
    session_timeout_minutes: int = 30
    
    # Product catalog
    num_products: int = int(os.getenv("NUM_PRODUCTS", "500"))
    num_categories: int = 20
    
    # Event probabilities (must sum to 1.0)
    event_weights: dict = None
    
    def __post_init__(self):
        if self.event_weights is None:
            self.event_weights = {
                "page_view": 0.35,
                "product_view": 0.30,
                "add_to_cart": 0.15,
                "remove_from_cart": 0.05,
                "checkout_start": 0.08,
                "purchase": 0.07,
            }


@dataclass  
class GCPConfig:
    """GCP configuration for BigQuery sink."""
    project_id: str = os.getenv("GCP_PROJECT_ID", "new-project-id")
    region: str = os.getenv("GCP_REGION", "europe-west1")
    dataset_id: str = "trendstream_analytics"
    
    # BigQuery tables
    revenue_table: str = "real_time_revenue"
    sessions_table: str = "user_sessions"
    fraud_table: str = "fraud_alerts"


# Global config instances
kafka_config = KafkaConfig()
simulator_config = SimulatorConfig()
gcp_config = GCPConfig()
