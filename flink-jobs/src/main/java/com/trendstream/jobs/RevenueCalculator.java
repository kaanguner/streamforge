package com.trendstream.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trendstream.model.OrderEvent;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Revenue Calculator Flink Job
 * 
 * Real-time revenue and GMV (Gross Merchandise Value) calculation.
 * Demonstrates Trendyol-relevant patterns:
 * - Tumbling window aggregation
 * - Multi-metric calculation
 * - Category-level breakdown
 * - Real-time KPI computation
 */
public class RevenueCalculator {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // Enable checkpointing
        env.enableCheckpointing(60000);
        
        // Configuration
        String bootstrapServers = System.getenv().getOrDefault(
            "KAFKA_BOOTSTRAP_SERVERS", "kafka:29092"
        );
        String inputTopic = "transactions.orders";
        String outputTopic = "analytics.revenue";
        
        // Create Kafka source
        KafkaSource<String> source = KafkaSource.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setTopics(inputTopic)
            .setGroupId("revenue-calculator")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();
        
        // Read orders
        DataStream<OrderEvent> orders = env
            .fromSource(
                source,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(10)),
                "Orders Source"
            )
            .map(json -> objectMapper.readValue(json, OrderEvent.class))
            .name("Parse Orders")
            .filter(order -> "CREATED".equals(order.getOrderStatus()) || 
                           "PAYMENT_COMPLETED".equals(order.getOrderStatus()))
            .name("Filter Valid Orders")
            .assignTimestampsAndWatermarks(
                WatermarkStrategy
                    .<OrderEvent>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                    .withTimestampAssigner((order, ts) -> order.getTimestamp())
            );
        
        // Calculate revenue metrics per minute
        DataStream<RevenueMetrics> revenueMetrics = orders
            .windowAll(TumblingEventTimeWindows.of(Time.minutes(1)))
            .aggregate(new RevenueAggregateFunction())
            .name("Revenue Aggregation");
        
        // Log metrics
        revenueMetrics.map(metrics -> {
            System.out.println("📊 Revenue Metrics: " + metrics);
            return metrics;
        }).name("Log Metrics");
        
        // Serialize and send to Kafka
        DataStream<String> metricsJson = revenueMetrics
            .map(metrics -> objectMapper.writeValueAsString(metrics))
            .name("Serialize Metrics");
        
        KafkaSink<String> sink = KafkaSink.<String>builder()
            .setBootstrapServers(bootstrapServers)
            .setRecordSerializer(
                KafkaRecordSerializationSchema.builder()
                    .setTopic(outputTopic)
                    .setValueSerializationSchema(new SimpleStringSchema())
                    .build()
            )
            .build();
        
        metricsJson.sinkTo(sink).name("Kafka Sink");
        
        env.execute("TrendStream - Revenue Calculator");
    }
    
    /**
     * Revenue metrics computed per window.
     */
    public static class RevenueMetrics implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private Long windowStart;
        private Long windowEnd;
        private Double gmv; // Gross Merchandise Value
        private Integer orderCount;
        private Double averageOrderValue;
        private Integer uniqueUsers;
        private Map<String, Double> revenueByCategory;
        private Map<String, Integer> ordersByRegion;
        private Integer firstTimeOrders;
        
        public RevenueMetrics() {
            this.revenueByCategory = new HashMap<>();
            this.ordersByRegion = new HashMap<>();
        }
        
        // Getters and Setters
        public Long getWindowStart() { return windowStart; }
        public void setWindowStart(Long windowStart) { this.windowStart = windowStart; }
        
        public Long getWindowEnd() { return windowEnd; }
        public void setWindowEnd(Long windowEnd) { this.windowEnd = windowEnd; }
        
        public Double getGmv() { return gmv; }
        public void setGmv(Double gmv) { this.gmv = gmv; }
        
        public Integer getOrderCount() { return orderCount; }
        public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }
        
        public Double getAverageOrderValue() { return averageOrderValue; }
        public void setAverageOrderValue(Double aov) { this.averageOrderValue = aov; }
        
        public Integer getUniqueUsers() { return uniqueUsers; }
        public void setUniqueUsers(Integer uniqueUsers) { this.uniqueUsers = uniqueUsers; }
        
        public Map<String, Double> getRevenueByCategory() { return revenueByCategory; }
        public void setRevenueByCategory(Map<String, Double> rbc) { this.revenueByCategory = rbc; }
        
        public Map<String, Integer> getOrdersByRegion() { return ordersByRegion; }
        public void setOrdersByRegion(Map<String, Integer> obr) { this.ordersByRegion = obr; }
        
        public Integer getFirstTimeOrders() { return firstTimeOrders; }
        public void setFirstTimeOrders(Integer fto) { this.firstTimeOrders = fto; }
        
        @Override
        public String toString() {
            return String.format(
                "RevenueMetrics{GMV=%.2f TRY, orders=%d, AOV=%.2f, users=%d}",
                gmv, orderCount, averageOrderValue, uniqueUsers
            );
        }
    }
    
    /**
     * Accumulator for revenue aggregation.
     */
    public static class RevenueAccumulator implements Serializable {
        double totalRevenue = 0.0;
        int orderCount = 0;
        java.util.Set<String> uniqueUsers = new java.util.HashSet<>();
        Map<String, Double> categoryRevenue = new HashMap<>();
        Map<String, Integer> regionOrders = new HashMap<>();
        int firstTimeOrders = 0;
        Long minTimestamp = Long.MAX_VALUE;
        Long maxTimestamp = Long.MIN_VALUE;
    }
    
    /**
     * Aggregate function for revenue calculation.
     */
    public static class RevenueAggregateFunction 
            implements AggregateFunction<OrderEvent, RevenueAccumulator, RevenueMetrics> {
        
        @Override
        public RevenueAccumulator createAccumulator() {
            return new RevenueAccumulator();
        }
        
        @Override
        public RevenueAccumulator add(OrderEvent order, RevenueAccumulator acc) {
            // Update totals
            if (order.getTotalAmount() != null) {
                acc.totalRevenue += order.getTotalAmount();
            }
            acc.orderCount++;
            acc.uniqueUsers.add(order.getUserId());
            
            // Track timestamps
            if (order.getTimestamp() < acc.minTimestamp) {
                acc.minTimestamp = order.getTimestamp();
            }
            if (order.getTimestamp() > acc.maxTimestamp) {
                acc.maxTimestamp = order.getTimestamp();
            }
            
            // Revenue by category
            if (order.getItems() != null) {
                for (OrderEvent.OrderItem item : order.getItems()) {
                    String category = item.getCategory();
                    if (category != null && item.getTotalPrice() != null) {
                        acc.categoryRevenue.merge(category, item.getTotalPrice(), Double::sum);
                    }
                }
            }
            
            // Orders by region
            String region = order.getShippingAddressRegion();
            if (region != null) {
                acc.regionOrders.merge(region, 1, Integer::sum);
            }
            
            // First time orders
            if (Boolean.TRUE.equals(order.getIsFirstOrder())) {
                acc.firstTimeOrders++;
            }
            
            return acc;
        }
        
        @Override
        public RevenueMetrics getResult(RevenueAccumulator acc) {
            RevenueMetrics metrics = new RevenueMetrics();
            metrics.setWindowStart(acc.minTimestamp);
            metrics.setWindowEnd(acc.maxTimestamp);
            metrics.setGmv(Math.round(acc.totalRevenue * 100.0) / 100.0);
            metrics.setOrderCount(acc.orderCount);
            metrics.setAverageOrderValue(
                acc.orderCount > 0 ? 
                    Math.round((acc.totalRevenue / acc.orderCount) * 100.0) / 100.0 : 
                    0.0
            );
            metrics.setUniqueUsers(acc.uniqueUsers.size());
            metrics.setRevenueByCategory(acc.categoryRevenue);
            metrics.setOrdersByRegion(acc.regionOrders);
            metrics.setFirstTimeOrders(acc.firstTimeOrders);
            return metrics;
        }
        
        @Override
        public RevenueAccumulator merge(RevenueAccumulator a, RevenueAccumulator b) {
            RevenueAccumulator merged = new RevenueAccumulator();
            merged.totalRevenue = a.totalRevenue + b.totalRevenue;
            merged.orderCount = a.orderCount + b.orderCount;
            merged.uniqueUsers.addAll(a.uniqueUsers);
            merged.uniqueUsers.addAll(b.uniqueUsers);
            merged.minTimestamp = Math.min(a.minTimestamp, b.minTimestamp);
            merged.maxTimestamp = Math.max(a.maxTimestamp, b.maxTimestamp);
            merged.firstTimeOrders = a.firstTimeOrders + b.firstTimeOrders;
            
            merged.categoryRevenue.putAll(a.categoryRevenue);
            b.categoryRevenue.forEach((k, v) -> 
                merged.categoryRevenue.merge(k, v, Double::sum)
            );
            
            merged.regionOrders.putAll(a.regionOrders);
            b.regionOrders.forEach((k, v) -> 
                merged.regionOrders.merge(k, v, Integer::sum)
            );
            
            return merged;
        }
    }
}
