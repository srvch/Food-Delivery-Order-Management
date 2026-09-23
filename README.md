# Food Delivery Order Management

A food delivery order management system built with Spring Boot: multi-city
restaurants, per-restaurant menu management, customer ordering, the full
order lifecycle, delivery-partner assignment, concurrency-safe stock and
payment handling, asynchronous status notifications, and restaurant ratings.

See `docs/superpowers/specs/2026-09-23-food-delivery-order-management-design.md`
for the full design rationale and `CLAUDE.md` for build/test instructions.

## Quick start

1. Start PostgreSQL and create a `fooddelivery` database/user (see
   `CLAUDE.md`), or rely on Testcontainers for tests only.
2. `./mvnw spring-boot:run`
3. A default admin account is seeded by Flyway: `admin@fooddelivery.com` /
   `Admin@123` — use it to create restaurants and their owner accounts.

## Running tests

`./mvnw test` (requires Docker running for Testcontainers-backed
integration tests).
