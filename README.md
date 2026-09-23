# Food Delivery Order Management

A Spring Boot backend for a food delivery order management system: multi-city
restaurants, per-restaurant menu management, customer ordering with
concurrency-safe stock and simulated payment, restaurant accept/reject,
delivery-partner assignment with contention handling, asynchronous status
notifications, and restaurant ratings.

Full design rationale: `docs/superpowers/specs/2026-09-23-food-delivery-order-management-design.md`
Implementation plan: `docs/superpowers/plans/2026-09-23-food-delivery-order-management.md`
Development conventions: `CLAUDE.md`

## Tech stack

Java 17, Spring Boot 3.3 (Web, Data JPA, Security, Validation), PostgreSQL,
Flyway, JJWT, JUnit 5, Mockito, Testcontainers, Awaitility.

## Running the app

1. Provide a PostgreSQL database matching `application.yml`
   (`jdbc:postgresql://localhost:5432/fooddelivery`, user/password
   `fooddelivery`), or point the `spring.datasource.*` properties at your own.
2. `./mvnw spring-boot:run`
3. A default admin account is seeded by Flyway migration `V2`:
   `admin@fooddelivery.com` / `Admin@123`.

## Running tests

`./mvnw test` — requires Docker running (Testcontainers starts a real
PostgreSQL instance for every integration test, so behavior — including the
pessimistic-lock and conditional-update concurrency tests — is verified
against real Postgres semantics, not an in-memory approximation).

## Roles and flows

- **Admin** (seeded, or created by another admin): manage cities
  (`/cities`), create restaurants with their owner account
  (`/admin/restaurants`), manage delivery partners
  (`/admin/delivery-partners`).
- **Restaurant Owner** (created by an admin, or reused across restaurants
  by email): manage their own restaurant's menu
  (`/restaurants/{id}/menu-items`), accept/reject orders
  (`/orders/{id}/accept`, `/orders/{id}/reject`), mark orders `PREPARING`
  (`/orders/{id}/status`), view their restaurant's orders
  (`/restaurants/{id}/orders`).
- **Customer** (self-registers via `/auth/register`): browse restaurants and
  menus (`/restaurants`, `/restaurants/{id}/menu`), place orders
  (`/orders`), view their own orders (`/orders`, `/orders/{id}`), rate a
  delivered order (`/orders/{id}/ratings`).
- **Delivery Partner** (self-registers via `/auth/register` with a
  `cityId`, starts inactive until an admin activates them): view open
  assignments in their city (`/assignments/open?cityId=`), accept one
  (`/assignments/{id}/accept`), drive an assigned order from `PREPARING`
  through `OUT_FOR_DELIVERY` to `DELIVERED` (`/orders/{id}/status`).
- **All authenticated users**: view their own notifications
  (`/notifications`), mark one read (`/notifications/{id}/read`).

## Key design points

- **Stock concurrency:** order placement locks every requested menu item
  with `SELECT ... FOR UPDATE` (ordered by id) inside one transaction, so
  concurrent orders for the same item can never oversell stock — proven by
  `OrderPlacementConcurrencyIT`.
- **Payment atomicity:** stock decrement, order creation, and the simulated
  payment charge happen in the same transaction; a declined charge rolls
  back everything, including the stock decrement — proven by
  `OrderPlacementIT#declinedPaymentRollsBackStockDecrement`.
- **Delivery-partner contention:** accepting an assignment is a single
  conditional `UPDATE ... WHERE status = 'OPEN'`; exactly one concurrent
  caller ever wins — proven by `DeliveryAssignmentConcurrencyIT`.
- **Async notifications:** every order/assignment event fans out via
  `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`, so the triggering
  HTTP call never waits on notification delivery.

## Assumptions

(See the design spec's Assumptions section for the full list.) Notably:
scope is intentionally limited to what the source requirement states — no
order cancellation, no restaurant open/close-hours gating, no
delivery-partner availability toggle, no idempotency-key handling; ratings
apply to the restaurant only.
