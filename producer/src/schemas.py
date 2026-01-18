"""
Avro Schemas for TrendStream Events
These schemas define the structure of events flowing through Kafka.
"""

# Clickstream Event Schema
CLICKSTREAM_SCHEMA = {
    "type": "record",
    "name": "ClickstreamEvent",
    "namespace": "com.trendstream.events",
    "doc": "User interaction events from the e-commerce platform",
    "fields": [
        {"name": "event_id", "type": "string", "doc": "Unique event identifier (UUID)"},
        {"name": "user_id", "type": "string", "doc": "User identifier"},
        {"name": "session_id", "type": "string", "doc": "Session identifier for grouping"},
        {
            "name": "event_type",
            "type": {
                "type": "enum",
                "name": "ClickEventType",
                "symbols": [
                    "PAGE_VIEW",
                    "PRODUCT_VIEW", 
                    "ADD_TO_CART",
                    "REMOVE_FROM_CART",
                    "CHECKOUT_START",
                    "PURCHASE"
                ]
            },
            "doc": "Type of user interaction"
        },
        {"name": "product_id", "type": ["null", "string"], "default": None, "doc": "Product ID if applicable"},
        {"name": "category", "type": ["null", "string"], "default": None, "doc": "Product category"},
        {"name": "price", "type": ["null", "double"], "default": None, "doc": "Product price in TRY"},
        {"name": "quantity", "type": ["null", "int"], "default": None, "doc": "Quantity for cart events"},
        {"name": "timestamp", "type": "long", "doc": "Event timestamp (epoch millis)"},
        {"name": "device_type", "type": "string", "doc": "Device: mobile, desktop, tablet"},
        {"name": "geo_region", "type": "string", "doc": "Geographic region: istanbul, ankara, izmir, etc."},
        {"name": "page_url", "type": ["null", "string"], "default": None, "doc": "Current page URL"},
        {"name": "referrer", "type": ["null", "string"], "default": None, "doc": "Referrer URL"}
    ]
}

# Transaction Order Schema
ORDER_SCHEMA = {
    "type": "record",
    "name": "OrderEvent",
    "namespace": "com.trendstream.events",
    "doc": "Order lifecycle events",
    "fields": [
        {"name": "order_id", "type": "string", "doc": "Unique order identifier"},
        {"name": "user_id", "type": "string", "doc": "User who placed the order"},
        {"name": "session_id", "type": "string", "doc": "Session in which order was placed"},
        {
            "name": "order_status",
            "type": {
                "type": "enum",
                "name": "OrderStatus",
                "symbols": [
                    "CREATED",
                    "PAYMENT_PENDING",
                    "PAYMENT_COMPLETED",
                    "CONFIRMED",
                    "SHIPPED",
                    "DELIVERED",
                    "CANCELLED",
                    "REFUNDED"
                ]
            },
            "doc": "Current order status"
        },
        {
            "name": "items",
            "type": {
                "type": "array",
                "items": {
                    "type": "record",
                    "name": "OrderItem",
                    "fields": [
                        {"name": "product_id", "type": "string"},
                        {"name": "product_name", "type": "string"},
                        {"name": "category", "type": "string"},
                        {"name": "quantity", "type": "int"},
                        {"name": "unit_price", "type": "double"},
                        {"name": "total_price", "type": "double"}
                    ]
                }
            },
            "doc": "Items in the order"
        },
        {"name": "subtotal", "type": "double", "doc": "Order subtotal before discounts"},
        {"name": "discount_amount", "type": "double", "doc": "Total discount applied"},
        {"name": "shipping_cost", "type": "double", "doc": "Shipping cost"},
        {"name": "total_amount", "type": "double", "doc": "Final order total"},
        {"name": "currency", "type": "string", "default": "TRY", "doc": "Currency code"},
        {"name": "payment_method", "type": "string", "doc": "Payment method: credit_card, debit_card, wallet"},
        {"name": "shipping_address_region", "type": "string", "doc": "Shipping region"},
        {"name": "timestamp", "type": "long", "doc": "Event timestamp (epoch millis)"},
        {"name": "is_first_order", "type": "boolean", "doc": "Whether this is user's first order"}
    ]
}

# Inventory Update Schema
INVENTORY_SCHEMA = {
    "type": "record",
    "name": "InventoryEvent",
    "namespace": "com.trendstream.events",
    "doc": "Inventory and pricing updates",
    "fields": [
        {"name": "event_id", "type": "string", "doc": "Unique event identifier"},
        {"name": "product_id", "type": "string", "doc": "Product identifier"},
        {
            "name": "event_type",
            "type": {
                "type": "enum",
                "name": "InventoryEventType",
                "symbols": [
                    "STOCK_UPDATE",
                    "STOCK_RESERVED",
                    "STOCK_RELEASED",
                    "PRICE_CHANGE",
                    "PRODUCT_ACTIVATED",
                    "PRODUCT_DEACTIVATED"
                ]
            },
            "doc": "Type of inventory event"
        },
        {"name": "previous_stock", "type": ["null", "int"], "default": None},
        {"name": "new_stock", "type": ["null", "int"], "default": None},
        {"name": "previous_price", "type": ["null", "double"], "default": None},
        {"name": "new_price", "type": ["null", "double"], "default": None},
        {"name": "warehouse_id", "type": "string", "doc": "Warehouse location"},
        {"name": "timestamp", "type": "long", "doc": "Event timestamp (epoch millis)"}
    ]
}

# Fraud Alert Schema (output from Flink)
FRAUD_ALERT_SCHEMA = {
    "type": "record",
    "name": "FraudAlert",
    "namespace": "com.trendstream.alerts",
    "doc": "Fraud detection alerts generated by Flink",
    "fields": [
        {"name": "alert_id", "type": "string", "doc": "Unique alert identifier"},
        {"name": "user_id", "type": "string", "doc": "Flagged user"},
        {"name": "order_id", "type": ["null", "string"], "default": None, "doc": "Related order if applicable"},
        {
            "name": "alert_type",
            "type": {
                "type": "enum",
                "name": "FraudAlertType",
                "symbols": [
                    "RAPID_ORDERS",
                    "LOCATION_ANOMALY",
                    "HIGH_VALUE_FIRST_ORDER",
                    "SUSPICIOUS_PATTERN"
                ]
            },
            "doc": "Type of fraud pattern detected"
        },
        {"name": "risk_score", "type": "double", "doc": "Risk score 0.0-1.0"},
        {"name": "description", "type": "string", "doc": "Human-readable alert description"},
        {"name": "detected_at", "type": "long", "doc": "Detection timestamp"},
        {"name": "related_events", "type": {"type": "array", "items": "string"}, "doc": "Related event IDs"}
    ]
}


def get_schema(schema_name: str) -> dict:
    """Get schema by name."""
    schemas = {
        "clickstream": CLICKSTREAM_SCHEMA,
        "order": ORDER_SCHEMA,
        "inventory": INVENTORY_SCHEMA,
        "fraud_alert": FRAUD_ALERT_SCHEMA,
    }
    return schemas.get(schema_name)
