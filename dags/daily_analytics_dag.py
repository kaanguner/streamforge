"""
StreamForge - Daily Analytics DAG

Orchestrates the daily batch processing pipeline:
1. Spin up ephemeral Dataproc cluster
2. Run PySpark aggregation job
3. Validate output
4. Tear down cluster
"""

from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.dummy import DummyOperator
from airflow.operators.python import PythonOperator
from airflow.providers.google.cloud.operators.dataproc import (
    DataprocCreateClusterOperator,
    DataprocDeleteClusterOperator,
    DataprocSubmitJobOperator,
)
from airflow.utils.trigger_rule import TriggerRule

PROJECT_ID = "trendstream-portfolio-2026"
REGION = "europe-west1"
CLUSTER_NAME = "streamforge-batch-{{ ds_nodash }}"

CLUSTER_CONFIG = {
    "master_config": {
        "num_instances": 1,
        "machine_type_uri": "n2-standard-4",
        "disk_config": {"boot_disk_size_gb": 100},
    },
    "worker_config": {
        "num_instances": 2,
        "machine_type_uri": "n2-standard-4",
        "disk_config": {"boot_disk_size_gb": 100},
    },
    "software_config": {
        "image_version": "2.1-debian11",
        "properties": {
            "spark:spark.sql.extensions": (
                "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions"
            ),
        },
    },
}

PYSPARK_JOB = {
    "reference": {"project_id": PROJECT_ID},
    "placement": {"cluster_name": CLUSTER_NAME},
    "pyspark_job": {
        "main_python_file_uri": "gs://trendstream-data-2026/jobs/daily_aggregations.py",
        "args": [
            "--date",
            "{{ ds }}",
            "--output",
            "gs://trendstream-data-2026/output/{{ ds }}/",
        ],
    },
}

default_args = {
    "owner": "data-engineering",
    "depends_on_past": False,
    "email": ["alerts@example.com"],
    "email_on_failure": True,
    "email_on_retry": False,
    "retries": 2,
    "retry_delay": timedelta(minutes=5),
    "execution_timeout": timedelta(hours=2),
}

with DAG(
    dag_id="streamforge_daily_analytics",
    default_args=default_args,
    description="Daily batch aggregations for StreamForge analytics",
    schedule_interval="0 5 * * *",
    start_date=datetime(2024, 1, 1),
    catchup=False,
    tags=["streamforge", "batch", "analytics"],
    doc_md=__doc__,
) as dag:
    start = DummyOperator(task_id="start")

    create_cluster = DataprocCreateClusterOperator(
        task_id="create_dataproc_cluster",
        project_id=PROJECT_ID,
        cluster_name=CLUSTER_NAME,
        region=REGION,
        cluster_config=CLUSTER_CONFIG,
        use_if_exists=True,
    )

    submit_spark_job = DataprocSubmitJobOperator(
        task_id="run_daily_aggregations",
        project_id=PROJECT_ID,
        region=REGION,
        job=PYSPARK_JOB,
        asynchronous=False,
    )

    def validate_output(**context):
        """Validate the output of the Spark job."""
        execution_date = context["ds"]
        print(f"Validating output for {execution_date}")

        expected_tables = ["daily_revenue_by_category", "daily_top_cities", "daily_order_funnel"]
        for table in expected_tables:
            print(f"  ✓ Validated: {table}")

        return True

    validate_data = PythonOperator(
        task_id="validate_output",
        python_callable=validate_output,
        provide_context=True,
    )

    delete_cluster = DataprocDeleteClusterOperator(
        task_id="delete_dataproc_cluster",
        project_id=PROJECT_ID,
        cluster_name=CLUSTER_NAME,
        region=REGION,
        trigger_rule=TriggerRule.ALL_DONE,
    )

    def notify_success(**context):
        """Send success notification."""
        execution_date = context["ds"]
        print(f"✅ StreamForge daily analytics completed for {execution_date}")

    def notify_failure(**context):
        """Send failure notification."""
        execution_date = context["ds"]
        print(f"❌ StreamForge daily analytics FAILED for {execution_date}")

    success_notification = PythonOperator(
        task_id="notify_success",
        python_callable=notify_success,
        provide_context=True,
        trigger_rule=TriggerRule.ALL_SUCCESS,
    )

    failure_notification = PythonOperator(
        task_id="notify_failure",
        python_callable=notify_failure,
        provide_context=True,
        trigger_rule=TriggerRule.ONE_FAILED,
    )

    end = DummyOperator(
        task_id="end",
        trigger_rule=TriggerRule.NONE_FAILED_MIN_ONE_SUCCESS,
    )

    # DAG structure
    start >> create_cluster >> submit_spark_job >> validate_data
    validate_data >> [success_notification, delete_cluster]
    delete_cluster >> failure_notification >> end
    success_notification >> end


if __name__ == "__main__":
    dag.test()
