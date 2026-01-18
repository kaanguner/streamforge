# TrendStream - Trendyol Architecture Patterns

This document maps TrendStream components to Trendyol's published data engineering patterns, demonstrating relevant streaming architecture knowledge.

## Pattern Mapping

### 1. Near Real-Time (NRT) Data Flows

**Trendyol's Approach:**
Trendyol uses NRT flows for operational, rapidly-changing data needs throughout the day, while T-1 flows handle analytical workloads using previous-day snapshots.

**TrendStream Implementation:**
- All clickstream, order, and inventory events are processed in real-time (<1 second latency)
- Session windows aggregate user behavior within 30-minute gaps
- Revenue metrics computed every minute for operational dashboards
- No batch processing dependencies for real-time analytics

```
Event → Kafka → Flink (NRT Processing) → BigQuery (Streaming Insert)
                    ↓
              Redis (Feature Store)
```

---

### 2. Kafka Consumer Patterns

**Trendyol's Approach:**
Trendyol's open-source `kafka-konsumer` project features:
- Transactional retry mechanisms
- Message header manipulation
- Consumer resume/pause functionality
- Consumer group scaling

**TrendStream Implementation:**
- Consumer groups per Flink job (`session-aggregator`, `fraud-detector`, `revenue-calculator`)
- Exactly-once semantics via Flink checkpointing
- Event-time watermarks for out-of-order event handling
- Backpressure handling through Flink's credit-based flow control

```java
// TrendStream uses Flink's Kafka connector with exactly-once
KafkaSource.<String>builder()
    .setBootstrapServers(bootstrapServers)
    .setGroupId("session-aggregator")  // Consumer group
    .setStartingOffsets(OffsetsInitializer.latest())
    .build();
```

---

### 3. Feature Store Architecture

**Trendyol's Approach:**
For search relevance systems, Trendyol:
1. Batch-computes features and publishes to Kafka topics
2. Kafka consumer ingests data into Couchbase NoSQL feature store
3. Real-time inference reads from feature store

**TrendStream Implementation:**
- User session features computed by `SessionAggregator`
- Features written to Redis for low-latency access
- Structure supports ML inference (future enhancement)

```
Clickstream → Kafka → Flink SessionAggregator → Redis
                                                   ↓
                                          ML Inference Service
```

---

### 4. Stateful Stream Processing

**Trendyol's Approach:**
Managing state for user personalization, session tracking, and fraud detection.

**TrendStream Implementation:**

| Job | State Type | Purpose |
|-----|------------|---------|
| SessionAggregator | Session Window State | Accumulate events per user session |
| FraudDetector | ValueState | Track first-order status per user |
| RevenueCalculator | Tumbling Window State | Aggregate revenue per time window |

```java
// FraudDetector uses ValueState to track user order history
private transient ValueState<Boolean> hasOrderedState;

@Override
public void open(Configuration parameters) {
    ValueStateDescriptor<Boolean> descriptor = new ValueStateDescriptor<>(
        "has-ordered", TypeInformation.of(Boolean.class)
    );
    hasOrderedState = getRuntimeContext().getState(descriptor);
}
```

---

### 5. Complex Event Processing (CEP)

**Trendyol's Approach:**
Real-time pattern detection for fraud, abuse, and anomaly detection.

**TrendStream Implementation:**
FraudDetector uses Flink CEP for pattern matching:

```java
// Detect 3+ orders within 5 minutes
Pattern<OrderEvent, ?> rapidOrderPattern = Pattern
    .<OrderEvent>begin("first").where(isOrder())
    .followedBy("second").where(isOrder())
    .followedBy("third").where(isOrder())
    .within(Time.minutes(5));
```

**Detection Patterns:**
- `RAPID_ORDERS`: >3 orders in 5 minutes
- `HIGH_VALUE_FIRST_ORDER`: First order > 5000 TRY
- Extensible for additional patterns

---

### 6. Schema Evolution

**Trendyol's Approach:**
Using Avro with Schema Registry for backward/forward compatibility.

**TrendStream Implementation:**
- Avro schemas defined in `producer/src/schemas.py`
- Schema Registry integration via Confluent platform
- JSON fallback for development simplicity

---

### 7. Monitoring & Observability

**Trendyol's Approach:**
Comprehensive monitoring of Kafka consumer lag, job health, and data quality.

**TrendStream Implementation:**

| Metric | Source | Dashboard |
|--------|--------|-----------|
| Consumer lag | Kafka UI | http://localhost:8080 |
| Job checkpoints | Flink UI | http://localhost:8082 |
| Event throughput | Flink metrics | Prometheus/Grafana |
| Business KPIs | BigQuery | Looker Studio |

---

## Interview Talking Points

When discussing this project in a Trendyol interview:

1. **Scale considerations**: "The architecture supports horizontal scaling via Kafka partitions and Flink parallelism. For Trendyol's traffic, I would..."

2. **Exactly-once semantics**: "Flink's checkpointing provides exactly-once guarantees, essential for financial calculations like GMV..."

3. **State management**: "For the fraud detector, I use ValueState to track user behavior across events, similar to how Trendyol might track..."

4. **Late event handling**: "Watermarks with 30-second bounded out-of-orderness handle late events while maintaining freshness..."

5. **Cost optimization**: "GKE Autopilot provides cost-efficient scaling for Flink workloads compared to fixed-size clusters..."

---

## References

- [Trendyol on Medium - Data Warehouse Architecture](https://medium.com/trendyol-tech)
- [Trendyol kafka-konsumer GitHub](https://github.com/Trendyol/kafka-konsumer)
- [Apache Flink Documentation](https://flink.apache.org/docs/)
- [Confluent Cloud on GCP](https://www.confluent.io/partners/google-cloud/)
