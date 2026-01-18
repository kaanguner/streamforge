package com.trendstream.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendstream.model.FraudAlert;
import com.trendstream.model.OrderEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternSelectFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

/**
 * Fraud Detector Flink Job with Dead Letter Queue (DLQ) Pattern
 *
 * <p>Real-time fraud detection using Complex Event Processing (CEP). Demonstrates production-grade
 * patterns:
 *
 * <ul>
 *   <li>Dead Letter Queue: Routes malformed/unparseable data to separate topic
 *   <li>CEP pattern matching for temporal fraud detection
 *   <li>Stateful processing with ValueState
 *   <li>Multi-rule fraud detection
 * </ul>
 *
 * <p>Detection Patterns:
 *
 * <ol>
 *   <li>RAPID_ORDERS: 3+ orders from same user within 5 minutes
 *   <li>HIGH_VALUE_FIRST_ORDER: First order > 5000 TRY
 * </ol>
 */
public class FraudDetector {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  // Thresholds
  private static final double HIGH_VALUE_THRESHOLD = 5000.0;

  // DLQ Side Output Tag - routes bad data without crashing the pipeline
  public static final OutputTag<String> DLQ_TAG = new OutputTag<String>("dlq-events") {};

  public static void main(String[] args) throws Exception {
    StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

    env.enableCheckpointing(60000);

    String bootstrapServers =
        System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "kafka:29092");
    String inputTopic = "transactions.orders";
    String outputTopic = "alerts.fraud";
    String dlqTopic = "dlq.fraud-detector";

    KafkaSource<String> source =
        KafkaSource.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setTopics(inputTopic)
            .setGroupId("fraud-detector")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();

    DataStream<String> rawMessages =
        env.fromSource(
            source,
            WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(10)),
            "Orders Source");

    // =====================================
    // DEAD LETTER QUEUE: Parse with fault tolerance
    // Bad data goes to DLQ topic instead of crashing
    // =====================================
    SingleOutputStreamOperator<OrderEvent> orders =
        rawMessages.process(new SafeJsonParser()).name("Parse Orders (DLQ-enabled)");

    // Route DLQ events to separate Kafka topic
    DataStream<String> dlqStream = orders.getSideOutput(DLQ_TAG);

    KafkaSink<String> dlqSink =
        KafkaSink.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setRecordSerializer(
                KafkaRecordSerializationSchema.builder()
                    .setTopic(dlqTopic)
                    .setValueSerializationSchema(new SimpleStringSchema())
                    .build())
            .build();

    dlqStream.sinkTo(dlqSink).name("DLQ Kafka Sink");

    // Log DLQ events for monitoring
    dlqStream
        .map(
            msg -> {
              System.err.println("⚠️ DLQ EVENT: " + msg);
              return msg;
            })
        .name("Log DLQ Events");

    // Assign timestamps after parsing
    DataStream<OrderEvent> timestampedOrders =
        orders
            .assignTimestampsAndWatermarks(
                WatermarkStrategy.<OrderEvent>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                    .withTimestampAssigner((order, ts) -> order.getTimestamp()))
            .name("Assign Timestamps");

    // =====================================
    // Pattern 1: Rapid Orders (CEP)
    // =====================================
    DataStream<OrderEvent> keyedOrders = timestampedOrders.keyBy(OrderEvent::getUserId);

    Pattern<OrderEvent, ?> rapidOrderPattern =
        Pattern.<OrderEvent>begin("first")
            .where(
                new SimpleCondition<OrderEvent>() {
                  @Override
                  public boolean filter(OrderEvent order) {
                    return "CREATED".equals(order.getOrderStatus());
                  }
                })
            .followedBy("second")
            .where(
                new SimpleCondition<OrderEvent>() {
                  @Override
                  public boolean filter(OrderEvent order) {
                    return "CREATED".equals(order.getOrderStatus());
                  }
                })
            .followedBy("third")
            .where(
                new SimpleCondition<OrderEvent>() {
                  @Override
                  public boolean filter(OrderEvent order) {
                    return "CREATED".equals(order.getOrderStatus());
                  }
                })
            .within(Time.minutes(5));

    PatternStream<OrderEvent> rapidOrderStream = CEP.pattern(keyedOrders, rapidOrderPattern);

    DataStream<FraudAlert> rapidOrderAlerts =
        rapidOrderStream
            .select(
                new PatternSelectFunction<OrderEvent, FraudAlert>() {
                  @Override
                  public FraudAlert select(Map<String, List<OrderEvent>> pattern) {
                    OrderEvent first = pattern.get("first").get(0);
                    OrderEvent third = pattern.get("third").get(0);

                    FraudAlert alert =
                        new FraudAlert(
                            UUID.randomUUID().toString(),
                            first.getUserId(),
                            FraudAlert.AlertType.RAPID_ORDERS,
                            0.85,
                            String.format(
                                "User placed 3 orders within 5 minutes. First: %s, Last: %s",
                                first.getOrderId(), third.getOrderId()));
                    alert.setOrderId(third.getOrderId());
                    return alert;
                  }
                })
            .name("Rapid Order Detection");

    // =====================================
    // Pattern 2: High Value First Order (Stateful)
    // =====================================
    DataStream<FraudAlert> highValueFirstOrderAlerts =
        timestampedOrders
            .keyBy(OrderEvent::getUserId)
            .process(new HighValueFirstOrderDetector())
            .name("High Value First Order Detection");

    // =====================================
    // Combine all alerts
    // =====================================
    DataStream<FraudAlert> allAlerts = rapidOrderAlerts.union(highValueFirstOrderAlerts);

    allAlerts
        .map(
            alert -> {
              System.out.println("🚨 FRAUD ALERT: " + alert);
              return alert;
            })
        .name("Log Alerts");

    DataStream<String> alertJson =
        allAlerts.map(alert -> objectMapper.writeValueAsString(alert)).name("Serialize Alerts");

    KafkaSink<String> sink =
        KafkaSink.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setRecordSerializer(
                KafkaRecordSerializationSchema.builder()
                    .setTopic(outputTopic)
                    .setValueSerializationSchema(new SimpleStringSchema())
                    .build())
            .build();

    alertJson.sinkTo(sink).name("Kafka Alert Sink");

    env.execute("TrendStream - Fraud Detector (DLQ-enabled)");
  }

  /**
   * Safe JSON parser that routes unparseable messages to Dead Letter Queue. This prevents "poison
   * pill" data from crashing the entire pipeline.
   */
  public static class SafeJsonParser extends ProcessFunction<String, OrderEvent> {

    @Override
    public void processElement(String json, Context ctx, Collector<OrderEvent> out) {
      try {
        OrderEvent event = objectMapper.readValue(json, OrderEvent.class);

        // Basic validation
        if (event.getUserId() == null || event.getOrderId() == null) {
          ctx.output(DLQ_TAG, formatDlqMessage(json, "Missing required fields: userId or orderId"));
          return;
        }

        out.collect(event);
      } catch (Exception e) {
        // Route to DLQ instead of crashing
        ctx.output(DLQ_TAG, formatDlqMessage(json, e.getMessage()));
      }
    }

    private String formatDlqMessage(String originalMessage, String errorReason) {
      return String.format(
          "{\"timestamp\":\"%s\",\"error\":\"%s\",\"original\":%s}",
          Instant.now().toString(), errorReason.replace("\"", "'"), originalMessage);
    }
  }

  /** Detects high-value first orders using Flink state. */
  public static class HighValueFirstOrderDetector
      extends KeyedProcessFunction<String, OrderEvent, FraudAlert> {

    private transient ValueState<Boolean> hasOrderedState;

    @Override
    public void open(Configuration parameters) {
      ValueStateDescriptor<Boolean> descriptor =
          new ValueStateDescriptor<>("has-ordered", TypeInformation.of(Boolean.class));
      hasOrderedState = getRuntimeContext().getState(descriptor);
    }

    @Override
    public void processElement(OrderEvent order, Context ctx, Collector<FraudAlert> out)
        throws Exception {

      Boolean hasOrdered = hasOrderedState.value();

      if (hasOrdered == null || !hasOrdered) {
        hasOrderedState.update(true);

        if (order.getTotalAmount() != null && order.getTotalAmount() > HIGH_VALUE_THRESHOLD) {
          FraudAlert alert =
              new FraudAlert(
                  UUID.randomUUID().toString(),
                  order.getUserId(),
                  FraudAlert.AlertType.HIGH_VALUE_FIRST_ORDER,
                  0.75,
                  String.format(
                      "First order from user is high value: %.2f TRY (threshold: %.2f)",
                      order.getTotalAmount(), HIGH_VALUE_THRESHOLD));
          alert.setOrderId(order.getOrderId());
          out.collect(alert);
        }
      }
    }
  }

  // Helper methods for tests
  public static double calculateRiskScore(double amount, boolean isFirstOrder) {
    if (amount < 0) return 0.95; // Negative amounts are highly suspicious
    if (amount == 0) return 0.0;

    double baseScore = Math.min(amount / 200000.0, 0.9);
    if (isFirstOrder) {
      baseScore += 0.1;
    }
    return Math.min(baseScore, 1.0);
  }

  public static FraudAlert createAlert(
      OrderEvent order, String alertType, double riskScore, String description) {
    FraudAlert alert =
        new FraudAlert(
            UUID.randomUUID().toString(),
            order.getUserId(),
            FraudAlert.AlertType.valueOf(alertType),
            riskScore,
            description);
    alert.setOrderId(order.getOrderId());
    alert.setAmount(order.getTotalAmount());
    return alert;
  }
}
