"""
TrendStream Event Simulator
Generates realistic e-commerce events for Kafka streaming pipeline.

Usage:
    python simulator.py --events 1000 --rate 100
    python simulator.py --infinite --rate 50
"""
import argparse
import json
import random
import time
import uuid
from datetime import datetime, timedelta
from typing import Dict, Generator, List, Optional

from confluent_kafka import Producer
from confluent_kafka.schema_registry import SchemaRegistryClient
from confluent_kafka.schema_registry.avro import AvroSerializer
from confluent_kafka.serialization import SerializationContext, MessageField

from config import kafka_config, simulator_config
from schemas import CLICKSTREAM_SCHEMA, ORDER_SCHEMA, INVENTORY_SCHEMA


# Turkish cities with population weights
REGIONS = {
    "istanbul": 0.35,
    "ankara": 0.12,
    "izmir": 0.10,
    "bursa": 0.08,
    "antalya": 0.07,
    "adana": 0.06,
    "konya": 0.05,
    "gaziantep": 0.05,
    "mersin": 0.04,
    "diyarbakir": 0.04,
    "kayseri": 0.04,
}

# Product categories inspired by Trendyol
CATEGORIES = [
    "electronics", "fashion_women", "fashion_men", "shoes", "bags",
    "cosmetics", "home_living", "supermarket", "baby_kids", "sports",
    "books", "toys", "jewelry", "watches", "automotive",
    "garden", "pet_supplies", "health", "office", "audio"
]

DEVICE_TYPES = ["mobile", "desktop", "tablet"]
DEVICE_WEIGHTS = [0.65, 0.25, 0.10]  # Mobile-first like Trendyol

PAYMENT_METHODS = ["credit_card", "debit_card", "wallet", "bank_transfer"]


class UserSession:
    """Represents an active user session."""
    
    def __init__(self, user_id: str):
        self.user_id = user_id
        self.session_id = str(uuid.uuid4())
        self.device_type = random.choices(DEVICE_TYPES, weights=DEVICE_WEIGHTS)[0]
        self.region = random.choices(
            list(REGIONS.keys()), 
            weights=list(REGIONS.values())
        )[0]
        self.cart: List[Dict] = []
        self.last_activity = datetime.now()
        self.is_first_order = random.random() < 0.15  # 15% new users
        self.viewed_products: List[str] = []
        
    def is_expired(self, timeout_minutes: int = 30) -> bool:
        """Check if session has timed out."""
        return datetime.now() - self.last_activity > timedelta(minutes=timeout_minutes)
    
    def touch(self):
        """Update last activity time."""
        self.last_activity = datetime.now()


class ProductCatalog:
    """Simulated product catalog."""
    
    def __init__(self, num_products: int = 500):
        self.products = self._generate_products(num_products)
        
    def _generate_products(self, count: int) -> List[Dict]:
        """Generate fake product catalog."""
        products = []
        for i in range(count):
            category = random.choice(CATEGORIES)
            base_price = self._get_category_base_price(category)
            price = round(base_price * random.uniform(0.5, 2.0), 2)
            
            products.append({
                "product_id": f"PRD-{i:06d}",
                "product_name": f"{category.replace('_', ' ').title()} Item {i}",
                "category": category,
                "price": price,
                "stock": random.randint(0, 1000),
            })
        return products
    
    def _get_category_base_price(self, category: str) -> float:
        """Get base price by category (in TRY)."""
        price_ranges = {
            "electronics": 2500,
            "fashion_women": 350,
            "fashion_men": 400,
            "shoes": 600,
            "bags": 800,
            "cosmetics": 200,
            "home_living": 500,
            "supermarket": 50,
            "baby_kids": 250,
            "sports": 450,
            "watches": 1500,
            "jewelry": 2000,
        }
        return price_ranges.get(category, 300)
    
    def get_random_product(self) -> Dict:
        """Get a random product."""
        return random.choice(self.products)
    
    def get_product(self, product_id: str) -> Optional[Dict]:
        """Get product by ID."""
        for p in self.products:
            if p["product_id"] == product_id:
                return p
        return None


class EventGenerator:
    """Generates realistic e-commerce events."""
    
    def __init__(self):
        self.catalog = ProductCatalog(simulator_config.num_products)
        self.active_sessions: Dict[str, UserSession] = {}
        self.user_order_counts: Dict[str, int] = {}
        
    def _get_or_create_session(self) -> UserSession:
        """Get existing session or create new one."""
        # Clean expired sessions
        expired = [
            uid for uid, sess in self.active_sessions.items()
            if sess.is_expired(simulator_config.session_timeout_minutes)
        ]
        for uid in expired:
            del self.active_sessions[uid]
        
        # 70% chance to use existing session, 30% new user
        if self.active_sessions and random.random() < 0.7:
            user_id = random.choice(list(self.active_sessions.keys()))
            session = self.active_sessions[user_id]
            session.touch()
            return session
        
        # Create new session
        user_id = f"USR-{random.randint(1, simulator_config.num_users):06d}"
        session = UserSession(user_id)
        session.is_first_order = self.user_order_counts.get(user_id, 0) == 0
        self.active_sessions[user_id] = session
        return session
    
    def generate_clickstream_event(self) -> Dict:
        """Generate a clickstream event."""
        session = self._get_or_create_session()
        
        # Weighted event selection based on cart state
        if session.cart:
            # More likely to checkout if items in cart
            weights = {
                "page_view": 0.20,
                "product_view": 0.20,
                "add_to_cart": 0.10,
                "remove_from_cart": 0.10,
                "checkout_start": 0.25,
                "purchase": 0.15,
            }
        else:
            weights = simulator_config.event_weights
        
        event_type = random.choices(
            list(weights.keys()),
            weights=list(weights.values())
        )[0]
        
        product = None
        if event_type in ["product_view", "add_to_cart"]:
            product = self.catalog.get_random_product()
            session.viewed_products.append(product["product_id"])
        elif event_type == "remove_from_cart" and session.cart:
            product = random.choice(session.cart)
            session.cart.remove(product)
        elif event_type == "purchase" and session.cart:
            # Will trigger order creation
            pass
        
        # Update cart for add events
        if event_type == "add_to_cart" and product:
            session.cart.append(product)
        
        event = {
            "event_id": str(uuid.uuid4()),
            "user_id": session.user_id,
            "session_id": session.session_id,
            "event_type": event_type.upper(),
            "product_id": product["product_id"] if product else None,
            "category": product["category"] if product else None,
            "price": product["price"] if product else None,
            "quantity": random.randint(1, 3) if product else None,
            "timestamp": int(datetime.now().timestamp() * 1000),
            "device_type": session.device_type,
            "geo_region": session.region,
            "page_url": f"/product/{product['product_id']}" if product else "/",
            "referrer": random.choice([None, "google", "instagram", "direct"]),
        }
        
        return event
    
    def generate_order_event(self, session: UserSession) -> Optional[Dict]:
        """Generate an order event from session cart."""
        if not session.cart:
            return None
        
        order_id = f"ORD-{uuid.uuid4().hex[:12].upper()}"
        
        items = []
        subtotal = 0
        for item in session.cart:
            qty = random.randint(1, 3)
            total_price = item["price"] * qty
            subtotal += total_price
            items.append({
                "product_id": item["product_id"],
                "product_name": item["product_name"],
                "category": item["category"],
                "quantity": qty,
                "unit_price": item["price"],
                "total_price": round(total_price, 2),
            })
        
        discount = round(subtotal * random.uniform(0, 0.2), 2)  # 0-20% discount
        shipping = 29.99 if subtotal < 150 else 0  # Free shipping over 150 TRY
        total = round(subtotal - discount + shipping, 2)
        
        # Track orders for first-order detection
        self.user_order_counts[session.user_id] = \
            self.user_order_counts.get(session.user_id, 0) + 1
        
        order = {
            "order_id": order_id,
            "user_id": session.user_id,
            "session_id": session.session_id,
            "order_status": "CREATED",
            "items": items,
            "subtotal": round(subtotal, 2),
            "discount_amount": discount,
            "shipping_cost": shipping,
            "total_amount": total,
            "currency": "TRY",
            "payment_method": random.choice(PAYMENT_METHODS),
            "shipping_address_region": session.region,
            "timestamp": int(datetime.now().timestamp() * 1000),
            "is_first_order": session.is_first_order,
        }
        
        # Clear cart after order
        session.cart = []
        
        return order
    
    def generate_inventory_event(self) -> Dict:
        """Generate an inventory update event."""
        product = self.catalog.get_random_product()
        event_type = random.choices(
            ["STOCK_UPDATE", "PRICE_CHANGE", "STOCK_RESERVED"],
            weights=[0.6, 0.3, 0.1]
        )[0]
        
        event = {
            "event_id": str(uuid.uuid4()),
            "product_id": product["product_id"],
            "event_type": event_type,
            "previous_stock": product["stock"],
            "new_stock": None,
            "previous_price": product["price"],
            "new_price": None,
            "warehouse_id": f"WH-{random.choice(['IST', 'ANK', 'IZM'])}-{random.randint(1,5)}",
            "timestamp": int(datetime.now().timestamp() * 1000),
        }
        
        if event_type in ["STOCK_UPDATE", "STOCK_RESERVED"]:
            delta = random.randint(-50, 100)
            event["new_stock"] = max(0, product["stock"] + delta)
            product["stock"] = event["new_stock"]
        
        if event_type == "PRICE_CHANGE":
            change_pct = random.uniform(-0.15, 0.15)  # -15% to +15%
            event["new_price"] = round(product["price"] * (1 + change_pct), 2)
            product["price"] = event["new_price"]
        
        return event


class KafkaEventProducer:
    """Kafka producer with Avro serialization."""
    
    def __init__(self):
        self.producer = Producer({
            "bootstrap.servers": kafka_config.bootstrap_servers,
            "acks": kafka_config.acks,
            "retries": kafka_config.retries,
            "linger.ms": kafka_config.linger_ms,
            "batch.size": kafka_config.batch_size,
        })
        
        # Schema Registry client (optional - falls back to JSON if unavailable)
        try:
            self.schema_registry = SchemaRegistryClient({
                "url": kafka_config.schema_registry_url
            })
            self.use_avro = True
            print(f"✓ Connected to Schema Registry at {kafka_config.schema_registry_url}")
        except Exception as e:
            print(f"⚠ Schema Registry unavailable, using JSON: {e}")
            self.use_avro = False
        
        self.events_sent = 0
        self.errors = 0
    
    def _delivery_callback(self, err, msg):
        """Callback for delivery confirmation."""
        if err:
            self.errors += 1
            print(f"✗ Delivery failed: {err}")
        else:
            self.events_sent += 1
    
    def send_event(self, topic: str, key: str, value: Dict):
        """Send event to Kafka topic."""
        try:
            # Serialize to JSON (Avro would require more setup)
            value_bytes = json.dumps(value).encode("utf-8")
            key_bytes = key.encode("utf-8") if key else None
            
            self.producer.produce(
                topic=topic,
                key=key_bytes,
                value=value_bytes,
                callback=self._delivery_callback
            )
            self.producer.poll(0)  # Trigger callbacks
            
        except Exception as e:
            print(f"✗ Error sending to {topic}: {e}")
            self.errors += 1
    
    def flush(self):
        """Flush pending messages."""
        self.producer.flush()
    
    def get_stats(self) -> Dict:
        """Get producer statistics."""
        return {
            "events_sent": self.events_sent,
            "errors": self.errors,
        }


def run_simulator(
    total_events: Optional[int] = None,
    events_per_second: int = 100,
):
    """Run the event simulator."""
    print("=" * 60)
    print("🚀 TrendStream Event Simulator")
    print("=" * 60)
    print(f"Kafka: {kafka_config.bootstrap_servers}")
    print(f"Rate: {events_per_second} events/sec")
    print(f"Events: {total_events or 'infinite'}")
    print("=" * 60)
    
    generator = EventGenerator()
    producer = KafkaEventProducer()
    
    event_count = 0
    start_time = time.time()
    last_report_time = start_time
    
    try:
        while total_events is None or event_count < total_events:
            batch_start = time.time()
            
            # Generate batch of events
            for _ in range(events_per_second):
                if total_events and event_count >= total_events:
                    break
                
                # 80% clickstream, 15% orders (from purchases), 5% inventory
                event_roll = random.random()
                
                if event_roll < 0.80:
                    # Clickstream event
                    event = generator.generate_clickstream_event()
                    producer.send_event(
                        topic=kafka_config.clickstream_topic,
                        key=event["user_id"],
                        value=event
                    )
                    
                    # Generate order if purchase event
                    if event["event_type"] == "PURCHASE":
                        session = generator.active_sessions.get(event["user_id"])
                        if session:
                            order = generator.generate_order_event(session)
                            if order:
                                producer.send_event(
                                    topic=kafka_config.orders_topic,
                                    key=order["user_id"],
                                    value=order
                                )
                
                elif event_roll < 0.95:
                    # Order status update (simulate order lifecycle)
                    # This would come from backend in real system
                    pass
                
                else:
                    # Inventory event
                    event = generator.generate_inventory_event()
                    producer.send_event(
                        topic=kafka_config.inventory_topic,
                        key=event["product_id"],
                        value=event
                    )
                
                event_count += 1
            
            # Report progress every 5 seconds
            current_time = time.time()
            if current_time - last_report_time >= 5:
                elapsed = current_time - start_time
                rate = event_count / elapsed
                stats = producer.get_stats()
                print(
                    f"📊 Events: {event_count:,} | "
                    f"Rate: {rate:.1f}/s | "
                    f"Sent: {stats['events_sent']:,} | "
                    f"Errors: {stats['errors']}"
                )
                last_report_time = current_time
            
            # Rate limiting - sleep to maintain target rate
            batch_elapsed = time.time() - batch_start
            sleep_time = max(0, 1.0 - batch_elapsed)
            if sleep_time > 0:
                time.sleep(sleep_time)
    
    except KeyboardInterrupt:
        print("\n⚠ Interrupted by user")
    
    finally:
        producer.flush()
        stats = producer.get_stats()
        elapsed = time.time() - start_time
        
        print("=" * 60)
        print("📈 Final Statistics")
        print("=" * 60)
        print(f"Total events generated: {event_count:,}")
        print(f"Events sent to Kafka: {stats['events_sent']:,}")
        print(f"Errors: {stats['errors']}")
        print(f"Duration: {elapsed:.1f} seconds")
        print(f"Average rate: {event_count / elapsed:.1f} events/sec")
        print("=" * 60)


def main():
    parser = argparse.ArgumentParser(
        description="TrendStream E-Commerce Event Simulator"
    )
    parser.add_argument(
        "--events", "-e",
        type=int,
        default=None,
        help="Total number of events to generate (default: infinite)"
    )
    parser.add_argument(
        "--rate", "-r",
        type=int,
        default=100,
        help="Events per second (default: 100)"
    )
    parser.add_argument(
        "--infinite",
        action="store_true",
        help="Run indefinitely"
    )
    
    args = parser.parse_args()
    
    total_events = None if args.infinite else (args.events or 1000)
    
    run_simulator(
        total_events=total_events,
        events_per_second=args.rate
    )


if __name__ == "__main__":
    main()
