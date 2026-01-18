# 🚀 StreamForge: Real-Time E-Commerce Analytics Pipeline

[![Live on GCP](https://img.shields.io/badge/LIVE-GCP%20Deployed-success?style=for-the-badge)](http://35.205.241.21)
[![Flink Dashboard](https://img.shields.io/badge/Flink-Dashboard-orange?style=for-the-badge)](http://35.205.241.21)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-7.5-red?style=flat-square)](https://kafka.apache.org/)
[![Flink](https://img.shields.io/badge/Apache%20Flink-1.18-blue?style=flat-square)](https://flink.apache.org/)

**A production-grade streaming analytics platform demonstrating expertise in Apache Kafka, Apache Flink, Debezium CDC, PySpark, and Google Cloud Platform.**

---

## 🔴 LIVE DEMO

| Service | URL | Status |
|---------|-----|--------|
| **Flink Dashboard** | [http://35.205.241.21](http://35.205.241.21) | ✅ LIVE |
| **GCP Console** | [console.cloud.google.com](https://console.cloud.google.com/home/dashboard?project=trendstream-portfolio-2026) | ✅ LIVE |
| **BigQuery** | [BigQuery Dataset](https://console.cloud.google.com/bigquery?project=trendstream-portfolio-2026) | ✅ LIVE |

---

## 🎯 Skills Demonstrated

| Technology | Implementation |
|------------|---------------|
| **Apache Kafka** | Confluent 7.5 with Schema Registry (Avro) |
| **Apache Flink (Java)** | Session windows, CEP fraud detection, stateful processing |
| **Apache Flink (Scala)** | CDC event processor with case classes |
| **Debezium CDC** | PostgreSQL WAL capture → Kafka |
| **PySpark** | Daily batch aggregations |
| **GKE + Kubernetes** | Flink on Autopilot with Workload Identity |
| **Terraform** | Full GCP infrastructure as code |
| **BigQuery** | Analytics tables with proper schemas |

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                              STREAMFORGE ARCHITECTURE                                  │
├──────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                        │
│  DATA SOURCES                                                                          │
│  ┌─────────────────┐   ┌─────────────────────────┐   ┌──────────────────┐            │
│  │ Event Producer  │   │ PostgreSQL + Debezium   │   │   Cloud Storage  │            │
│  │    (Python)     │   │        (CDC)            │   │    (Iceberg)     │            │
│  └────────┬────────┘   └───────────┬─────────────┘   └────────┬─────────┘            │
│           │                        │                          │                       │
│           ▼                        ▼                          ▼                       │
│  ┌────────────────────────────────────────────────────────────────────────────────┐  │
│  │                    APACHE KAFKA (Confluent 7.5)                                 │  │
│  │  clickstream.events │ transactions.orders │ cdc.public.* │ alerts.fraud         │  │
│  └────────────────────────────────────────────────────────────────────────────────┘  │
│           │                        │                          │                       │
│           ▼                        ▼                          ▼                       │
│  ┌──────────────────────────────────────┐   ┌────────────────────────────────────┐  │
│  │        APACHE FLINK (GKE)            │   │        APACHE SPARK (Batch)        │  │
│  │  ┌─────────────┐ ┌─────────────────┐ │   │  ┌──────────────────────────────┐  │  │
│  │  │Session      │ │Fraud Detector   │ │   │  │ Daily Aggregations (PySpark) │  │  │
│  │  │Aggregator   │ │(CEP Patterns)   │ │   │  │ • Revenue by category        │  │  │
│  │  │(Java)       │ │(Java)           │ │   │  │ • Top regions                │  │  │
│  │  └─────────────┘ └─────────────────┘ │   │  │ • Conversion funnel          │  │  │
│  │  ┌─────────────┐ ┌─────────────────┐ │   │  └──────────────────────────────┘  │  │
│  │  │Revenue      │ │CDC Processor    │ │   └────────────────────────────────────┘  │
│  │  │Calculator   │ │(Scala)          │ │                                           │
│  │  │(Java)       │ │                 │ │                                           │
│  │  └─────────────┘ └─────────────────┘ │                                           │
│  └──────────────────────────────────────┘                                           │
│           │                                                                          │
│           ▼                                                                          │
│  ┌────────────────────────────────────────────────────────────────────────────────┐  │
│  │                          DATA SINKS (GCP)                                       │  │
│  │  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐   ┌─────────────────────┐ │  │
│  │  │   BigQuery  │   │   Redis     │   │    GCS      │   │  alerts.fraud       │ │  │
│  │  │  Analytics  │   │Feature Store│   │  Iceberg    │   │  (Kafka topic)      │ │  │
│  │  └─────────────┘   └─────────────┘   └─────────────┘   └─────────────────────┘ │  │
│  └────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                        │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🚀 Quick Start

### Local Development

```bash
# 1. Start infrastructure (Kafka, Flink, PostgreSQL, Debezium)
docker-compose up -d

# 2. Register Debezium CDC connector
.\cdc\register-connector.ps1

# 3. Build Flink jobs (Java + Scala)
cd flink-jobs && mvn clean package

# 4. Run event producer
cd ../producer && pip install -r requirements.txt
python src/simulator.py --events 1000
```

### Access Points

| Service | Local URL |
|---------|-----------|
| Kafka UI | http://localhost:8080 |
| Flink Dashboard | http://localhost:8082 |
| Schema Registry | http://localhost:8081 |

---

## 📁 Project Structure

```
streamforge/
├── docker-compose.yml          # 12-service local stack
├── producer/                   # Python event simulator (Avro)
├── flink-jobs/                 # Streaming jobs
│   ├── src/main/java/          # Java jobs (3)
│   └── src/main/scala/         # Scala jobs (1)
├── spark/                      # PySpark batch jobs
├── cdc/                        # Debezium CDC configuration
│   ├── init-db.sql             # PostgreSQL schema
│   └── postgres-connector.json # Debezium config
├── k8s/                        # Kubernetes manifests
│   └── flink-deployment.yaml   # GKE deployment
└── infrastructure/terraform/   # GCP IaC
```

---

## 🔧 Streaming Jobs

### Flink Jobs (Real-time, <1s latency)

| Job | Language | Purpose | Window Type |
|-----|----------|---------|-------------|
| `SessionAggregator` | Java | User session analytics | Session (30-min gap) |
| `FraudDetector` | Java | CEP fraud pattern matching | Pattern-based |
| `RevenueCalculator` | Java | Real-time revenue metrics | Tumbling (1-min) |
| `CdcEventProcessor` | **Scala** | Process database changes | Continuous |

### Batch Job (PySpark)

| Job | Purpose |
|-----|---------|
| `daily_aggregations.py` | Daily revenue, top regions, conversion funnel |

---

## 🔄 Change Data Capture (Debezium)

Real-time database change capture:

```
PostgreSQL → Debezium → Kafka → Flink
```

**CDC Topics Created:**
- `cdc.public.products` - Price changes, stock updates
- `cdc.public.orders` - Order status transitions
- `cdc.public.order_items` - Line item changes

---

## ☁️ GCP Deployment

Fully deployed to Google Cloud Platform:

| Resource | Details |
|----------|---------|
| **GKE Cluster** | Autopilot, europe-west1 |
| **BigQuery Dataset** | 3 analytics tables |
| **GCS Bucket** | Checkpoints, Iceberg data |
| **Service Account** | Workload Identity enabled |

**Deploy yourself:**
```bash
cd infrastructure/terraform
terraform init && terraform apply
```

---

## 🛠️ Challenges Overcome

| Challenge | Solution |
|-----------|----------|
| Windows port conflicts | Remapped Kafka to 29092 |
| Zookeeper healthcheck | Used Confluent's `cub zk-ready` |
| Schema Registry connectivity | Fixed internal Docker networking |
| Terraform IAM race condition | Added explicit `depends_on` |
| GKE auth plugin | Installed gke-gcloud-auth-plugin |

---

## 📊 Key Patterns Implemented

- **Event-Time Processing** with watermarks for late data
- **Session Windows** for user journey analysis
- **Complex Event Processing (CEP)** for fraud detection
- **Stateful Processing** with RocksDB backend
- **Exactly-Once Semantics** via Flink checkpointing
- **Schema Evolution** with Avro + Schema Registry
- **Change Data Capture** with Debezium

---

## 👨‍💻 Author

**Kaan Guner** - [GitHub](https://github.com/kaanguner) | [LinkedIn](https://linkedin.com/in/kaanguner)

---

## 📄 License

MIT License - See [LICENSE](LICENSE)
