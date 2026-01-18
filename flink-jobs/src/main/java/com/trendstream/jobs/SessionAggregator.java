package com.trendstream.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendstream.model.ClickstreamEvent;
import com.trendstream.model.SessionAggregate;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

/**
 * Session Aggregator Flink Job
 *
 * <p>Aggregates user sessions using session windows with 30-minute inactivity gap. Demonstrates
 * Trendyol-relevant patterns: - Event-time processing with watermarks - Session window aggregation
 * - Stateful processing - Exactly-once semantics
 */
public class SessionAggregator {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public static void main(String[] args) throws Exception {
    // Get execution environment
    StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

    // Enable checkpointing for exactly-once semantics
    env.enableCheckpointing(60000); // 60 seconds

    // Configuration
    String bootstrapServers =
        System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "kafka:29092");
    String inputTopic = "clickstream.events";
    String outputTopic = "analytics.sessions";

    // Create Kafka source
    KafkaSource<String> source =
        KafkaSource.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setTopics(inputTopic)
            .setGroupId("session-aggregator")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();

    // Read from Kafka with watermarks
    DataStream<ClickstreamEvent> clickstream =
        env.fromSource(
                source,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(30))
                    .withIdleness(Duration.ofMinutes(1)),
                "Clickstream Source")
            .map(json -> objectMapper.readValue(json, ClickstreamEvent.class))
            .name("Parse JSON")
            .assignTimestampsAndWatermarks(
                WatermarkStrategy.<ClickstreamEvent>forBoundedOutOfOrderness(Duration.ofSeconds(30))
                    .withTimestampAssigner((event, timestamp) -> event.getTimestamp()))
            .name("Assign Timestamps");

    // Aggregate sessions with 30-minute gap
    DataStream<SessionAggregate> sessions =
        clickstream
            .keyBy(ClickstreamEvent::getUserId)
            .window(EventTimeSessionWindows.withGap(Time.minutes(30)))
            .aggregate(new SessionAggregateFunction())
            .name("Session Aggregation");

    // Log sessions (for debugging)
    sessions
        .map(
            s -> {
              System.out.println("Session completed: " + s);
              return s;
            })
        .name("Log Sessions");

    // Serialize and write to Kafka
    DataStream<String> sessionJson =
        sessions.map(session -> objectMapper.writeValueAsString(session)).name("Serialize to JSON");

    KafkaSink<String> sink =
        KafkaSink.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setRecordSerializer(
                KafkaRecordSerializationSchema.builder()
                    .setTopic(outputTopic)
                    .setValueSerializationSchema(new SimpleStringSchema())
                    .build())
            .build();

    sessionJson.sinkTo(sink).name("Kafka Sink");

    // Execute
    env.execute("TrendStream - Session Aggregator");
  }

  /**
   * Aggregate function for session windows. Accumulates clickstream events into session aggregates.
   */
  public static class SessionAggregateFunction
      implements AggregateFunction<ClickstreamEvent, SessionAccumulator, SessionAggregate> {

    @Override
    public SessionAccumulator createAccumulator() {
      return new SessionAccumulator();
    }

    @Override
    public SessionAccumulator add(ClickstreamEvent event, SessionAccumulator acc) {
      // Initialize on first event
      if (acc.userId == null) {
        acc.userId = event.getUserId();
        acc.sessionId = event.getSessionId();
        acc.deviceType = event.getDeviceType();
        acc.geoRegion = event.getGeoRegion();
        acc.sessionStart = event.getTimestamp();
        acc.sessionEnd = event.getTimestamp();
      }

      // Update timestamps
      if (event.getTimestamp() < acc.sessionStart) {
        acc.sessionStart = event.getTimestamp();
      }
      if (event.getTimestamp() > acc.sessionEnd) {
        acc.sessionEnd = event.getTimestamp();
      }

      // Count events by type
      switch (event.getEventType()) {
        case "PAGE_VIEW":
          acc.pageViews++;
          break;
        case "PRODUCT_VIEW":
          acc.productViews++;
          if (event.getCategory() != null) {
            acc.categoryCounts.merge(event.getCategory(), 1, Integer::sum);
          }
          break;
        case "ADD_TO_CART":
          acc.addToCartCount++;
          break;
        case "PURCHASE":
          acc.purchaseCount++;
          if (event.getPrice() != null) {
            acc.totalRevenue +=
                event.getPrice() * (event.getQuantity() != null ? event.getQuantity() : 1);
          }
          break;
      }

      return acc;
    }

    @Override
    public SessionAggregate getResult(SessionAccumulator acc) {
      SessionAggregate result = new SessionAggregate();
      result.setUserId(acc.userId);
      result.setSessionId(acc.sessionId);
      result.setSessionStart(acc.sessionStart);
      result.setSessionEnd(acc.sessionEnd);
      result.setDurationSeconds((acc.sessionEnd - acc.sessionStart) / 1000);
      result.setPageViews(acc.pageViews);
      result.setProductViews(acc.productViews);
      result.setAddToCartCount(acc.addToCartCount);
      result.setPurchaseCount(acc.purchaseCount);
      result.setTotalRevenue(acc.totalRevenue);
      result.setDeviceType(acc.deviceType);
      result.setGeoRegion(acc.geoRegion);

      // Find primary category
      result.setPrimaryCategory(
          acc.categoryCounts.entrySet().stream()
              .max(Map.Entry.comparingByValue())
              .map(Map.Entry::getKey)
              .orElse(null));

      return result;
    }

    @Override
    public SessionAccumulator merge(SessionAccumulator a, SessionAccumulator b) {
      // Merge two accumulators (for session window merging)
      SessionAccumulator merged = new SessionAccumulator();
      merged.userId = a.userId;
      merged.sessionId = a.sessionId;
      merged.deviceType = a.deviceType;
      merged.geoRegion = a.geoRegion;
      merged.sessionStart = Math.min(a.sessionStart, b.sessionStart);
      merged.sessionEnd = Math.max(a.sessionEnd, b.sessionEnd);
      merged.pageViews = a.pageViews + b.pageViews;
      merged.productViews = a.productViews + b.productViews;
      merged.addToCartCount = a.addToCartCount + b.addToCartCount;
      merged.purchaseCount = a.purchaseCount + b.purchaseCount;
      merged.totalRevenue = a.totalRevenue + b.totalRevenue;

      // Merge category counts
      merged.categoryCounts.putAll(a.categoryCounts);
      b.categoryCounts.forEach((k, v) -> merged.categoryCounts.merge(k, v, Integer::sum));

      return merged;
    }
  }

  /** Accumulator for session aggregation. */
  public static class SessionAccumulator {
    String userId;
    String sessionId;
    String deviceType;
    String geoRegion;
    Long sessionStart = Long.MAX_VALUE;
    Long sessionEnd = Long.MIN_VALUE;
    int pageViews = 0;
    int productViews = 0;
    int addToCartCount = 0;
    int purchaseCount = 0;
    double totalRevenue = 0.0;
    Map<String, Integer> categoryCounts = new HashMap<>();
  }
}
