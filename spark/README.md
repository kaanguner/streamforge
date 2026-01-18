# TrendStream Spark Batch Processing

This directory contains PySpark batch processing jobs for daily aggregations and analytics.

## Jobs

| Job | Purpose |
|-----|---------|
| `daily_aggregations.py` | Daily order/revenue aggregations |
| `data_quality.py` | Data quality checks |

## Quick Start

```bash
# Local testing with Docker Spark
docker run -it --rm \
  -v $(pwd):/app \
  -e SPARK_HOME=/opt/spark \
  bitnami/spark:3.5 \
  spark-submit /app/jobs/daily_aggregations.py

# GCP Dataproc
gcloud dataproc jobs submit pyspark \
  --cluster=trendstream-spark \
  --region=europe-west1 \
  gs://trendstream-code/spark/daily_aggregations.py
```

## Why PySpark (not Scala)?

- **Faster development** - Python syntax, easier debugging
- **Same performance** - PySpark compiles to same JVM execution plan
- **Industry standard** - Most batch analytics use PySpark
- **Trendyol context** - Scala for streaming (Flink/Kafka), Python for batch (common pattern)
