package com.trendstream.jobs

import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.api.common.functions.MapFunction
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * CDC Event Processor - SCALA FLINK JOB
 * 
 * Processes Change Data Capture events from Debezium/PostgreSQL.
 * Demonstrates:
 *   - Scala streaming with Flink
 *   - CDC event processing
 *   - Real-time inventory tracking
 *   - Price change analytics
 * 
 * This job is written in Scala to demonstrate proficiency in both
 * Java and Scala for streaming workloads (Trendyol requirement).
 */
object CdcEventProcessor {
  
  def main(args: Array[String]): Unit = {
    
    // Create Flink execution environment (using Java API for compatibility)
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.enableCheckpointing(60000)
    
    // Configuration
    val bootstrapServers = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
    
    println("=" * 60)
    println("🚀 TrendStream - CDC Event Processor (Scala)")
    println("=" * 60)
    println(s"Kafka: $bootstrapServers")
    println("Processing CDC events from: cdc.public.products, cdc.public.orders")
    println("=" * 60)
    
    // === Stream 1: Product CDC Events ===
    val productCdcSource = KafkaSource.builder[String]()
      .setBootstrapServers(bootstrapServers)
      .setTopics("cdc.public.products")
      .setGroupId("cdc-product-processor-scala")
      .setStartingOffsets(OffsetsInitializer.latest())
      .setValueOnlyDeserializer(new SimpleStringSchema())
      .build()
    
    val productStream = env.fromSource(
      productCdcSource, 
      WatermarkStrategy.noWatermarks[String](), 
      "Product CDC Source"
    )
    
    // Process product changes - log price changes
    productStream
      .map(new ProductChangeMapper())
      .filter((event: ProductEvent) => event.operation == "u") // Only updates
      .map(new ProductLogMapper())
      .name("Log Price Changes")
    
    // === Stream 2: Order CDC Events ===
    val orderCdcSource = KafkaSource.builder[String]()
      .setBootstrapServers(bootstrapServers)
      .setTopics("cdc.public.orders")
      .setGroupId("cdc-order-processor-scala")
      .setStartingOffsets(OffsetsInitializer.latest())
      .setValueOnlyDeserializer(new SimpleStringSchema())
      .build()
    
    val orderStream = env.fromSource(
      orderCdcSource,
      WatermarkStrategy.noWatermarks[String](),
      "Order CDC Source"
    )
    
    // Process order status changes - log new orders
    orderStream
      .map(new OrderChangeMapper())
      .filter((event: OrderEvent) => event.orderId > 0)
      .map(new OrderLogMapper())
      .name("Log Order Changes")
    
    // Execute
    env.execute("TrendStream - CDC Event Processor (Scala)")
  }
}

// === MapFunction Classes for Type Safety ===

class ProductChangeMapper extends MapFunction[String, ProductEvent] {
  @transient private lazy val mapper = new ObjectMapper()
  
  override def map(json: String): ProductEvent = {
    try {
      val node = mapper.readTree(json)
      ProductEvent(
        productId = Option(node.get("product_id")).map(_.asInt()).getOrElse(0),
        sku = Option(node.get("sku")).map(_.asText()).getOrElse(""),
        name = Option(node.get("name")).map(_.asText()).getOrElse(""),
        price = Option(node.get("price")).map(_.asDouble()).getOrElse(0.0),
        operation = Option(node.get("__op")).map(_.asText()).getOrElse("r")
      )
    } catch {
      case _: Exception => ProductEvent(0, "", "", 0.0, "error")
    }
  }
}

class ProductLogMapper extends MapFunction[ProductEvent, String] {
  override def map(event: ProductEvent): String = {
    println(s"💰 Price Change: ${event.sku} (${event.name}) -> ${event.price} TRY")
    s"""{"type":"price_change","sku":"${event.sku}","price":${event.price}}"""
  }
}

class OrderChangeMapper extends MapFunction[String, OrderEvent] {
  @transient private lazy val mapper = new ObjectMapper()
  
  override def map(json: String): OrderEvent = {
    try {
      val node = mapper.readTree(json)
      OrderEvent(
        orderId = Option(node.get("order_id")).map(_.asInt()).getOrElse(0),
        orderNumber = Option(node.get("order_number")).map(_.asText()).getOrElse(""),
        status = Option(node.get("order_status")).map(_.asText()).getOrElse(""),
        totalAmount = Option(node.get("total_amount")).map(_.asDouble()).getOrElse(0.0),
        city = Option(node.get("shipping_city")).map(_.asText()).getOrElse(""),
        operation = Option(node.get("__op")).map(_.asText()).getOrElse("r")
      )
    } catch {
      case _: Exception => OrderEvent(0, "", "", 0.0, "", "error")
    }
  }
}

class OrderLogMapper extends MapFunction[OrderEvent, String] {
  override def map(event: OrderEvent): String = {
    val emoji = event.operation match {
      case "c" => "🆕" // Create
      case "u" => "📝" // Update  
      case "d" => "❌" // Delete
      case _ => "📦"
    }
    println(s"$emoji Order: ${event.orderNumber} - ${event.status} (${event.totalAmount} TRY) -> ${event.city}")
    s"""{"type":"order_change","order":"${event.orderNumber}","status":"${event.status}"}"""
  }
}

// === Case Classes for CDC Events ===

case class ProductEvent(
  productId: Int,
  sku: String,
  name: String,
  price: Double,
  operation: String
)

case class OrderEvent(
  orderId: Int,
  orderNumber: String,
  status: String,
  totalAmount: Double,
  city: String,
  operation: String
)
