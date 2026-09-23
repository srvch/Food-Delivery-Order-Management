CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL CHECK (role IN ('ADMIN','RESTAURANT_OWNER','CUSTOMER','DELIVERY_PARTNER')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE cities (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE restaurants (
    id BIGSERIAL PRIMARY KEY,
    city_id BIGINT NOT NULL REFERENCES cities(id),
    owner_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500) NOT NULL,
    avg_rating NUMERIC(3,2) NOT NULL DEFAULT 0,
    rating_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE menu_items (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    name VARCHAR(255) NOT NULL,
    price NUMERIC(10,2) NOT NULL,
    stock_quantity INT NOT NULL,
    available BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE delivery_partner_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    city_id BIGINT NOT NULL REFERENCES cities(id),
    active BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES users(id),
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PLACED','ACCEPTED','PREPARING','OUT_FOR_DELIVERY','DELIVERED','REJECTED')),
    total_amount NUMERIC(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    menu_item_id BIGINT NOT NULL REFERENCES menu_items(id),
    quantity INT NOT NULL,
    unit_price_at_order NUMERIC(10,2) NOT NULL
);

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id),
    amount NUMERIC(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE delivery_assignments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN','ACCEPTED')),
    partner_id BIGINT REFERENCES users(id),
    offered_at TIMESTAMP NOT NULL DEFAULT now(),
    accepted_at TIMESTAMP
);

CREATE TABLE ratings (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id),
    rater_id BIGINT NOT NULL REFERENCES users(id),
    score INT NOT NULL CHECK (score BETWEEN 1 AND 5),
    review VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT NOT NULL REFERENCES users(id),
    order_id BIGINT NOT NULL REFERENCES orders(id),
    message VARCHAR(1000) NOT NULL,
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
