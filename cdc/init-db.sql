-- TrendStream CDC: PostgreSQL Initial Schema
-- This database simulates a Trendyol-like e-commerce system for CDC

-- Enable logical replication (required for Debezium)
-- Note: Also set in docker-compose via wal_level=logical

-- =============================================================
-- TABLES
-- =============================================================

-- Products table
CREATE TABLE products (
    product_id SERIAL PRIMARY KEY,
    sku VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    subcategory VARCHAR(100),
    brand VARCHAR(100),
    price DECIMAL(10, 2) NOT NULL,
    original_price DECIMAL(10, 2),
    stock_quantity INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    seller_id VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Customers table
CREATE TABLE customers (
    customer_id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(20),
    city VARCHAR(100),
    district VARCHAR(100),
    registration_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_premium BOOLEAN DEFAULT false,
    total_orders INTEGER DEFAULT 0
);

-- Orders table
CREATE TABLE orders (
    order_id SERIAL PRIMARY KEY,
    order_number VARCHAR(50) UNIQUE NOT NULL,
    customer_id INTEGER REFERENCES customers(customer_id),
    order_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(10, 2) NOT NULL,
    discount_amount DECIMAL(10, 2) DEFAULT 0,
    shipping_amount DECIMAL(10, 2) DEFAULT 0,
    payment_method VARCHAR(50),
    shipping_city VARCHAR(100),
    shipping_district VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Order items table
CREATE TABLE order_items (
    item_id SERIAL PRIMARY KEY,
    order_id INTEGER REFERENCES orders(order_id),
    product_id INTEGER REFERENCES products(product_id),
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    total_price DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Inventory movements (for real-time stock tracking)
CREATE TABLE inventory_movements (
    movement_id SERIAL PRIMARY KEY,
    product_id INTEGER REFERENCES products(product_id),
    movement_type VARCHAR(20) NOT NULL, -- 'IN', 'OUT', 'ADJUSTMENT'
    quantity INTEGER NOT NULL,
    reason VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================
-- SAMPLE DATA (Turkish e-commerce style)
-- =============================================================

-- Sample Products (Turkish brands and categories)
INSERT INTO products (sku, name, category, subcategory, brand, price, original_price, stock_quantity, seller_id) VALUES
('SKU-001', 'Samsung Galaxy S24 Ultra', 'Elektronik', 'Cep Telefonu', 'Samsung', 64999.00, 69999.00, 150, 'SELLER-001'),
('SKU-002', 'Apple iPhone 15 Pro Max', 'Elektronik', 'Cep Telefonu', 'Apple', 74999.00, 79999.00, 100, 'SELLER-001'),
('SKU-003', 'Dyson V15 Detect', 'Ev Aletleri', 'Elektrikli Süpürge', 'Dyson', 29999.00, 34999.00, 50, 'SELLER-002'),
('SKU-004', 'Nike Air Force 1', 'Moda', 'Ayakkabı', 'Nike', 3499.00, 3999.00, 500, 'SELLER-003'),
('SKU-005', 'Adidas Ultraboost', 'Moda', 'Ayakkabı', 'Adidas', 4299.00, 4999.00, 300, 'SELLER-003'),
('SKU-006', 'Philips Airfryer XXL', 'Ev Aletleri', 'Mutfak', 'Philips', 8999.00, 10999.00, 200, 'SELLER-002'),
('SKU-007', 'Apple MacBook Pro 14', 'Elektronik', 'Bilgisayar', 'Apple', 89999.00, 94999.00, 75, 'SELLER-001'),
('SKU-008', 'Sony WH-1000XM5', 'Elektronik', 'Kulaklık', 'Sony', 9999.00, 11999.00, 250, 'SELLER-004'),
('SKU-009', 'Mavi Erkek T-Shirt', 'Moda', 'Giyim', 'Mavi', 499.00, 699.00, 1000, 'SELLER-005'),
('SKU-010', 'LC Waikiki Kadın Elbise', 'Moda', 'Giyim', 'LC Waikiki', 799.00, 999.00, 800, 'SELLER-005');

-- Sample Customers (Turkish names and cities)
INSERT INTO customers (email, first_name, last_name, phone, city, district, is_premium, total_orders) VALUES
('ahmet.yilmaz@email.com', 'Ahmet', 'Yılmaz', '+905551234567', 'Istanbul', 'Kadıköy', true, 15),
('ayse.demir@email.com', 'Ayşe', 'Demir', '+905559876543', 'Ankara', 'Çankaya', false, 5),
('mehmet.kaya@email.com', 'Mehmet', 'Kaya', '+905553334444', 'Izmir', 'Karşıyaka', true, 22),
('fatma.ozturk@email.com', 'Fatma', 'Öztürk', '+905555556666', 'Istanbul', 'Beşiktaş', false, 3),
('mustafa.celik@email.com', 'Mustafa', 'Çelik', '+905557778888', 'Bursa', 'Nilüfer', true, 8),
('zeynep.arslan@email.com', 'Zeynep', 'Arslan', '+905552223333', 'Antalya', 'Muratpaşa', false, 2),
('ali.sahin@email.com', 'Ali', 'Şahin', '+905554445555', 'Istanbul', 'Üsküdar', true, 30),
('elif.aydin@email.com', 'Elif', 'Aydın', '+905556667777', 'Ankara', 'Keçiören', false, 1),
('can.yildiz@email.com', 'Can', 'Yıldız', '+905558889999', 'Izmir', 'Bornova', true, 12),
('selin.kurt@email.com', 'Selin', 'Kurt', '+905551112222', 'Istanbul', 'Şişli', false, 7);

-- Sample Orders
INSERT INTO orders (order_number, customer_id, order_status, total_amount, discount_amount, shipping_amount, payment_method, shipping_city, shipping_district) VALUES
('ORD-2024-0001', 1, 'DELIVERED', 64999.00, 5000.00, 0.00, 'CREDIT_CARD', 'Istanbul', 'Kadıköy'),
('ORD-2024-0002', 2, 'SHIPPED', 3499.00, 0.00, 29.99, 'BANK_TRANSFER', 'Ankara', 'Çankaya'),
('ORD-2024-0003', 3, 'PROCESSING', 89999.00, 5000.00, 0.00, 'CREDIT_CARD', 'Izmir', 'Karşıyaka'),
('ORD-2024-0004', 4, 'PENDING', 799.00, 0.00, 19.99, 'CASH_ON_DELIVERY', 'Istanbul', 'Beşiktaş'),
('ORD-2024-0005', 5, 'DELIVERED', 29999.00, 3000.00, 0.00, 'CREDIT_CARD', 'Bursa', 'Nilüfer');

-- Sample Order Items
INSERT INTO order_items (order_id, product_id, quantity, unit_price, total_price) VALUES
(1, 1, 1, 64999.00, 64999.00),
(2, 4, 1, 3499.00, 3499.00),
(3, 7, 1, 89999.00, 89999.00),
(4, 10, 1, 799.00, 799.00),
(5, 3, 1, 29999.00, 29999.00);

-- =============================================================
-- TRIGGER FUNCTION for updated_at
-- =============================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_products_modtime
    BEFORE UPDATE ON products
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_orders_modtime
    BEFORE UPDATE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =============================================================
-- PUBLICATION for Debezium (only needed tables)
-- =============================================================
CREATE PUBLICATION debezium_publication FOR TABLE products, orders, order_items, inventory_movements;

-- Grant replication permission
ALTER USER trendstream WITH REPLICATION;
