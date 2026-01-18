# StreamForge Flink Helm Chart

A Helm chart for deploying Apache Flink on Kubernetes with production-ready configurations.

## Installation

```bash
# Install with default values
helm install streamforge-flink ./charts/streamforge-flink

# Install with custom values
helm install streamforge-flink ./charts/streamforge-flink -f values-prod.yaml

# Upgrade deployment
helm upgrade streamforge-flink ./charts/streamforge-flink --set taskmanager.replicas=4
```

## Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `environment` | Environment name | `development` |
| `jobmanager.replicas` | Number of JobManager replicas | `1` |
| `jobmanager.resources.limits.memory` | JobManager memory limit | `2048Mi` |
| `taskmanager.replicas` | Number of TaskManager replicas | `2` |
| `taskmanager.resources.limits.memory` | TaskManager memory limit | `4096Mi` |
| `taskmanager.taskSlots` | Task slots per TaskManager | `2` |
| `checkpointing.enabled` | Enable checkpointing | `true` |
| `checkpointing.interval` | Checkpoint interval (ms) | `60000` |
| `checkpointing.storagePath` | GCS path for checkpoints | `gs://...` |
| `metrics.enabled` | Enable Prometheus metrics | `true` |

## Environment Overrides

Create environment-specific values files:

```yaml
# values-prod.yaml
environment: production

taskmanager:
  replicas: 8
  resources:
    limits:
      memory: "8192Mi"
      cpu: "4000m"

jobmanager:
  resources:
    limits:
      memory: "4096Mi"
```

## Deploy to Different Environments

```bash
# Development
helm install streamforge-flink ./charts/streamforge-flink

# Staging
helm install streamforge-flink ./charts/streamforge-flink \
  --set environment=staging \
  --set taskmanager.replicas=4

# Production
helm install streamforge-flink ./charts/streamforge-flink \
  -f values-prod.yaml
```

## Uninstall

```bash
helm uninstall streamforge-flink
```
