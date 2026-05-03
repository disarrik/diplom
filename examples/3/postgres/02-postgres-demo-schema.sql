\c "postgres-demo"

CREATE TABLE IF NOT EXISTS d1 (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    customer_name TEXT NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS d2 (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    src_id BIGINT,
    customer_name TEXT,
    amount NUMERIC(12, 2),
    status TEXT,
    loaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS d3 (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    src_id BIGINT,
    customer_name TEXT,
    amount NUMERIC(12, 2),
    status TEXT,
    loaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO d1 (customer_name, amount, status) VALUES
    ('Alice', 100.00, 'completed'),
    ('Bob',   250.50, 'processing'),
    ('Carol', 75.00,  'pending');
