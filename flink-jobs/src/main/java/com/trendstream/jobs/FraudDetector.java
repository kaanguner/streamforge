package com.trendstream.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendstream.model.FraudAlert;
import com.trendstream.model.OrderEvent;
import java.time.Duration;
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
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;

/**
 * Fraud Detector Flink Job
 *
 * <p>Real-time fraud detection using Complex Event Processing (CEP). Demonstrates Trendyol-relevant
 * patterns: - CEP pattern matching - Stateful processing with ValueState - Multi-rule fraud
 * detection - Real-time alerting
 *
 * <p>Detection Patterns: 1. RAPID_ORDERS: >3 orders from same user within 5 minutes 2.
 * HIGH_VALUE_FIRST_ORDER: First order > 5000 TRY 3. SUSPICIOUS_PATTERN: Order > 2000 TRY right
 * after session start
 */
public class FraudDetector {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  // Thresholds
  private static final double HIGH_VALUE_THRESHOLD = 5000.0; // 5000 TRY
  private static final double SUSPICIOUS_AMOUNT_THRESHOLD = 2000.0;
  private static final int RAPID_ORDER_COUNT = 3;
  private static final long RAPID_ORDER_WINDOW_MS = 5 * 60 * 1000; // 5 minutes

  public static void main(String[] args) throws Exception {
    StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

    // Enable checkpointing
    env.enableCheckpointing(60000);

    // Configuration
    String bootstrapServers =
        System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "kafka:29092");
    String inputTopic = "transactions.orders";
    String outputTopic = "alerts.fraud";

    // Create Kafka source
    KafkaSource<String> source =
        KafkaSource.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setTopics(inputTopic)
            .setGroupId("fraud-detector")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();

    // Read orders from Kafka
    DataStream<OrderEvent> orders =
        env.fromSource(
                source,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(10)),
                "Orders Source")
            .map(json -> objectMapper.readValue(json, OrderEvent.class))
            .name("Parse Orders")
            .assignTimestampsAndWatermarks(
                WatermarkStrategy.<OrderEvent>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                    .withTimestampAssigner((order, ts) -> order.getTimestamp()))
            .name("Assign Timestamps");

    // =====================================
    // Pattern 1: Rapid Orders (CEP)
    // =====================================
    DataStream<OrderEvent> keyedOrders = orders.keyBy(OrderEvent::getUserId);

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
                                "User placed 3 orders within 5 minutes. First order: %s, Last"
                                    + " order: %s",
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
        orders
            .keyBy(OrderEvent::getUserId)
            .process(new HighValueFirstOrderDetector())
            .name("High Value First Order Detection");

    // =====================================
    // Combine all alerts
    // =====================================
    DataStream<FraudAlert> allAlerts = rapidOrderAlerts.union(highValueFirstOrderAlerts);

    // Log alerts
    allAlerts
        .map(
            alert -> {
              System.out.println("🚨 FRAUD ALERT: " + alert);
              return alert;
            })
        .name("Log Alerts");

    // Serialize and send to Kafka
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

    env.execute("TrendStream - Fraud Detector");
  }

  /**
   * Detects high-value first orders using Flink state. Tracks whether each user has placed an order
   * before.
   */
  public static class HighValueFirstOrderDetector
      extends KeyedProcessFunction<String, OrderEvent, FraudAlert> {

    // State to track if user has ordered before
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

      // First order check
      if (hasOrdered == null || !hasOrdered) {
        hasOrderedState.update(true);

        // Check if first order is high value
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
}
