"""
TrendStream PySpark Batch Processing - Daily Aggregations

This job runs daily to compute:
- Daily revenue aggregations by category
- Top products by revenue/units sold
- Customer cohort analysis
- Output to Apache Iceberg tables

Demonstrates:
- PySpark batch processing
- BigQuery/Iceberg integration
- Trendyol-style analytics queries
"""

from pyspark.sql import SparkSession
from pyspark.sql import functions as F
from pyspark.sql.window import Window
from datetime import datetime, timedelta
import os


def create_spark_session():
    """Create Spark session with Iceberg support."""
    return (SparkSession.builder
        .appName("TrendStream - Daily Aggregations")
        .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
        .config("spark.sql.catalog.trendstream", "org.apache.iceberg.spark.SparkCatalog")
        .config("spark.sql.catalog.trendstream.type", "hadoop")
        .config("spark.sql.catalog.trendstream.warehouse", os.getenv("ICEBERG_WAREHOUSE", "gs://trendstream-data/iceberg"))
        .getOrCreate())


def load_orders_data(spark, date_str: str):
    """
    Load orders from BigQuery or local parquet for a given date.
    In production, this would read from BigQuery.
    """
    # For demo, create sample data
    # In production: spark.read.format("bigquery").option("table", "trendstream.orders").load()
    
    sample_data = [
        ("ORD-001", "USR-001", "2024-01-17", "Elektronik", "Cep Telefonu", 64999.00, 1, "Istanbul", "DELIVERED"),
        ("ORD-002", "USR-002", "2024-01-17", "Moda", "Ayakkabı", 3499.00, 2, "Ankara", "DELIVERED"),
        ("ORD-003", "USR-003", "2024-01-17", "Elektronik", "Kulaklık", 9999.00, 1, "Izmir", "SHIPPED"),
        ("ORD-004", "USR-001", "2024-01-17", "Ev Aletleri", "Elektrikli Süpürge", 29999.00, 1, "Istanbul", "DELIVERED"),
        ("ORD-005", "USR-004", "2024-01-17", "Moda", "Giyim", 799.00, 3, "Bursa", "PROCESSING"),
        ("ORD-006", "USR-005", "2024-01-17", "Elektronik", "Bilgisayar", 89999.00, 1, "Antalya", "DELIVERED"),
        ("ORD-007", "USR-006", "2024-01-17", "Moda", "Ayakkabı", 4299.00, 1, "Istanbul", "DELIVERED"),
        ("ORD-008", "USR-007", "2024-01-17", "Ev Aletleri", "Mutfak", 8999.00, 1, "Ankara", "SHIPPED"),
    ]
    
    columns = ["order_id", "customer_id", "order_date", "category", "subcategory", 
               "amount", "quantity", "city", "status"]
    
    return spark.createDataFrame(sample_data, columns)


def compute_daily_revenue(orders_df):
    """
    Compute daily revenue metrics by category.
    Mirrors Trendyol's GMV reporting.
    """
    return (orders_df
        .filter(F.col("status") == "DELIVERED")
        .groupBy("order_date", "category")
        .agg(
            F.sum("amount").alias("gmv"),  # Gross Merchandise Value
            F.count("order_id").alias("order_count"),
            F.sum("quantity").alias("units_sold"),
            F.avg("amount").alias("aov"),  # Average Order Value
            F.countDistinct("customer_id").alias("unique_customers")
        )
        .withColumn("revenue_per_customer", F.col("gmv") / F.col("unique_customers"))
        .orderBy(F.desc("gmv")))


def compute_top_cities(orders_df, top_n: int = 10):
    """
    Compute top cities by revenue.
    Important for regional logistics planning.
    """
    return (orders_df
        .filter(F.col("status") == "DELIVERED")
        .groupBy("order_date", "city")
        .agg(
            F.sum("amount").alias("gmv"),
            F.count("order_id").alias("order_count")
        )
        .withColumn("rank", F.row_number().over(
            Window.partitionBy("order_date").orderBy(F.desc("gmv"))
        ))
        .filter(F.col("rank") <= top_n))


def compute_conversion_funnel(orders_df):
    """
    Compute order status funnel metrics.
    Shows delivery success rates.
    """
    total = orders_df.count()
    
    status_counts = (orders_df
        .groupBy("status")
        .agg(
            F.count("order_id").alias("count"),
            F.sum("amount").alias("gmv")
        )
        .withColumn("percentage", (F.col("count") / total * 100).cast("decimal(5,2)"))
        .orderBy(F.desc("count")))
    
    return status_counts


def write_to_iceberg(df, table_name: str, mode: str = "append"):
    """
    Write DataFrame to Iceberg table.
    Supports append, overwrite, and merge.
    """
    warehouse = os.getenv("ICEBERG_WAREHOUSE", "gs://trendstream-data/iceberg")
    
    # In production:
    # df.writeTo(f"trendstream.{table_name}").append()
    
    # For local testing, write to parquet
    output_path = f"./output/{table_name}"
    df.write.mode(mode).parquet(output_path)
    print(f"✅ Written to {output_path}")


def main():
    print("=" * 60)
    print("🚀 TrendStream - Daily Batch Aggregations (PySpark)")
    print("=" * 60)
    
    # Initialize Spark
    spark = create_spark_session()
    spark.sparkContext.setLogLevel("WARN")
    
    # Process date (default: yesterday)
    process_date = (datetime.now() - timedelta(days=1)).strftime("%Y-%m-%d")
    print(f"📅 Processing date: {process_date}")
    
    # Load data
    print("\n📊 Loading orders data...")
    orders_df = load_orders_data(spark, process_date)
    orders_df.cache()
    print(f"   Total orders: {orders_df.count()}")
    
    # Compute aggregations
    print("\n📈 Computing daily revenue by category...")
    daily_revenue = compute_daily_revenue(orders_df)
    daily_revenue.show()
    
    print("\n🌍 Computing top cities...")
    top_cities = compute_top_cities(orders_df)
    top_cities.show()
    
    print("\n📊 Computing order funnel...")
    funnel = compute_conversion_funnel(orders_df)
    funnel.show()
    
    # Write results (in production, write to Iceberg/BigQuery)
    print("\n💾 Saving results...")
    write_to_iceberg(daily_revenue, "daily_revenue_by_category")
    write_to_iceberg(top_cities, "daily_top_cities")
    write_to_iceberg(funnel, "daily_order_funnel")
    
    # Summary
    total_gmv = orders_df.filter(F.col("status") == "DELIVERED").agg(F.sum("amount")).collect()[0][0]
    print("\n" + "=" * 60)
    print("📈 Daily Summary")
    print("=" * 60)
    print(f"   Total GMV: ₺{total_gmv:,.2f}")
    print(f"   Orders Processed: {orders_df.count()}")
    print("=" * 60)
    
    spark.stop()
    print("\n✅ Batch processing complete!")


if __name__ == "__main__":
    main()
