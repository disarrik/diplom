\c mydb

CREATE TABLE IF NOT EXISTS "order" (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    customer_name TEXT NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS order_dwh (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    order_id BIGINT,
    customer_name TEXT,
    amount NUMERIC(12, 2),
    loaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO "order" (customer_name, amount, status) VALUES
    ('Alice', 100.00, 'completed'),
    ('Bob',   250.50, 'processing'),
    ('Carol', 75.00,  'pending');
