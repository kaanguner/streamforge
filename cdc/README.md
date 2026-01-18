# TrendStream CDC Directory

This directory contains the Change Data Capture (CDC) pipeline using **Debezium** to stream PostgreSQL changes to Kafka.

## Files

| File | Purpose |
|------|---------|
| `init-db.sql` | PostgreSQL schema and sample Turkish e-commerce data |
| `postgres-connector.json` | Debezium connector configuration |
| `register-connector.ps1` | Script to register the connector |

## Quick Start

```powershell
# 1. Start all services (from trendstream directory)
docker-compose up -d

# 2. Wait for services to be healthy (~2 minutes)
docker-compose ps

# 3. Register the Debezium connector
.\cdc\register-connector.ps1
```

## How CDC Works

```
PostgreSQL (Source)          Debezium              Kafka              Flink (Consumer)
┌─────────────────┐      ┌──────────────┐      ┌──────────┐      ┌─────────────────┐
│ INSERT/UPDATE/  │──WAL─▶│  Debezium    │─────▶│ cdc.*    │─────▶│  CDC Event      │
│ DELETE events   │      │  Connector   │      │  topics  │      │  Processor      │
└─────────────────┘      └──────────────┘      └──────────┘      └─────────────────┘
```

## CDC Topics Created

| Topic | Source Table |
|-------|--------------|
| `cdc.public.products` | Product catalog changes |
| `cdc.public.orders` | Order lifecycle events |
| `cdc.public.order_items` | Order line items |
| `cdc.public.inventory_movements` | Stock changes |

## Testing CDC

```powershell
# Update a product price
docker exec -it trendstream-postgres psql -U trendstream -d ecommerce -c "UPDATE products SET price = 65999.00 WHERE sku = 'SKU-001';"

# Insert a new order
docker exec -it trendstream-postgres psql -U trendstream -d ecommerce -c "
INSERT INTO orders (order_number, customer_id, order_status, total_amount, payment_method, shipping_city) 
VALUES ('ORD-2024-0099', 1, 'PENDING', 4999.00, 'CREDIT_CARD', 'Istanbul');
"

# Check Kafka UI for CDC events: http://localhost:8080
```

## Interview Talking Points

> "I implemented real-time Change Data Capture using Debezium, which captures PostgreSQL WAL (Write-Ahead Log) entries and streams them to Kafka. This is the same pattern used at Trendyol for syncing database changes to downstream systems."

> "The connector uses the `pgoutput` plugin which is PostgreSQL's native logical replication, avoiding the need for external plugins. The `ExtractNewRecordState` transform flattens the Debezium envelope for easier downstream processing."
