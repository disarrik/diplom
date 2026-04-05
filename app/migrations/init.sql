CREATE TABLE IF NOT EXISTS "order" (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    customer_name TEXT NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS order_dwh (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    order_id BIGINT,
    customer_name TEXT,
    amount NUMERIC(12, 2),
    loaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO "order" (customer_name, amount) VALUES
    ('Alice', 100.00),
    ('Bob',   250.50),
    ('Carol', 75.00);
