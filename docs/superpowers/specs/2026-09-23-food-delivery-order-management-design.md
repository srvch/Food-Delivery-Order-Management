# Food Delivery Order Management — Design Spec

Date: 2026-09-23
Status: Approved

## 1. Purpose & Source

This system implements the "Food Delivery Order Management" take-home
assignment: a Spring Boot backend for multi-city restaurants, per-restaurant
menu management, customer ordering, the full order lifecycle, delivery-partner
assignment, concurrency-safe stock/payment handling, asynchronous status
fan-out, and post-delivery ratings.

The source requirement (`Food Delivery Order Management.pdf`) is intentionally
open-ended: scoping decisions, entity design, and feature depth are explicitly
part of what's evaluated. This document records those decisions and the
reasoning behind them.

**Out of scope** (per the assignment): UI/frontend, deployment/CI/CD,
distributed systems/microservices, advanced auth (OAuth/SSO/MFA),
production-grade observability.

## 2. Architecture & Tech Stack

- **Framework:** Spring Boot 3.x, Java 21, Maven.
- **Persistence:** PostgreSQL via Spring Data JPA, schema versioned with
  Flyway migrations.
- **Auth:** Spring Security with stateless JWT bearer tokens.
- **Async fan-out:** Spring `ApplicationEventPublisher` +
  `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` (in-process — no
  external broker, since distributed messaging is out of scope).
- **Testing:** JUnit 5, Mockito, Testcontainers (Postgres), MockMvc/WebTestClient,
  Awaitility (for asserting async side effects).

**Why Postgres over H2:** the core evaluated behavior is concurrency safety
(no overselling stock, single-winner partner assignment). Postgres gives real
row-level locking (`SELECT ... FOR UPDATE`) and transaction isolation
semantics; H2 does not reliably replicate this, which would make the most
important tests less trustworthy. Testcontainers gives a real Postgres in CI
without any manual setup.

**Why JWT over Basic Auth:** the brief calls for "basic role-based access
control," not "basic auth" — JWT is still simple to implement and reason
about, but is a more realistic shape for a REST API design than sending
credentials on every request, and each request carries its role/identity
without a session store.

**Module layout** — package-by-feature, one Spring Boot module:

```
com.fooddelivery
├── city          (Admin: cities)
├── restaurant     (Admin/Owner: restaurants, menu items)
├── order          (Customer: order placement, lifecycle, state machine)
├── delivery       (Partner: assignment offers, accept/status updates)
├── rating         (Customer: post-delivery ratings/reviews)
├── notification   (async fan-out: events -> persisted notifications)
├── user           (accounts, roles, auth)
└── common         (shared exceptions, config, security)
```

Each package owns its entity, repository, service, controller, and DTOs, and
is testable in isolation behind its service interface.

## 3. Domain Model

| Entity | Key fields | Notes |
|---|---|---|
| `User` | id, email, password (bcrypt), role enum, enabled | Single table + role enum; role-specific data lives in linked profile entities. |
| `City` | id, name, active | Admin-managed. |
| `Restaurant` | id, city FK, owner FK, name, address, avgRating (rollup) | |
| `MenuItem` | id, restaurant FK, name, price, stockQuantity, available | Row-locked on order placement. |
| `Order` | id, customer FK, restaurant FK, status enum, totalAmount, timestamps | |
| `OrderItem` | id, order FK, menuItem FK, quantity, unitPriceAtOrder | Price snapshot protects history from later menu price changes. |
| `Payment` | id, order FK (1:1), amount, createdAt | Represents a *successful* charge only — a declined charge throws and rolls back the whole order-placement transaction (see §5), so there's no durable failed-payment state to model. |
| `DeliveryPartnerProfile` | id, user FK, city FK, active (admin-managed) | Admin approves/deactivates delivery partners. |
| `DeliveryAssignment` | id, order FK (1:1), status (OPEN/ACCEPTED), acceptedBy FK (nullable), offeredAt, acceptedAt | Contention point: conditional update, not a lock. No CANCELLED state — nothing in scope can produce one, since order rejection only happens before an assignment exists. |
| `Rating` | id, order FK (unique), rater FK, score (1-5), review, createdAt | Rates the restaurant for a delivered order — the requirement only says "rate," with no separate delivery-partner target. |
| `Notification` | id, recipient FK, order FK, message, read, createdAt | Persisted result of async fan-out. |

## 4. Order Lifecycle & State Machine

```
PLACED --accept(owner)--> ACCEPTED --start-preparing--> PREPARING
                                                          |
                                              out-for-delivery (partner)
                                                          v
                                                  OUT_FOR_DELIVERY --deliver(partner)--> DELIVERED
PLACED --reject(owner)--> REJECTED
```

Who may trigger each transition:

- Restaurant owner: `PLACED -> ACCEPTED`, `PLACED -> REJECTED`,
  `ACCEPTED -> PREPARING`.
- Delivery partner (must be the assigned partner): `PREPARING ->
  OUT_FOR_DELIVERY`, `OUT_FOR_DELIVERY -> DELIVERED`. Moving to
  `OUT_FOR_DELIVERY` requires the order's `DeliveryAssignment` to already be
  `ACCEPTED` (see §6).

Both owner and partner status changes go through the same
`POST /orders/{id}/status` endpoint; the service validates the caller's role
and ownership/assignment against the requested target status, not just
whether the transition is legal in the abstract.

Transitions are enforced via an explicit transition map in the order service
(a `Map<OrderStatus, Set<OrderStatus>>` of legal next-states) — one place to
read and extend the whole state machine, rather than scattered conditionals.
An illegal transition returns 409 Conflict with a descriptive error body.

Rejecting an order refunds the simulated payment and restores menu-item
stock, in the same transaction as the status change.

## 5. Order Placement — Atomicity & Concurrency

This is the core evaluated guarantee: "Order placement must atomically
reflect item stock, order state, and payment," and "concurrent orders for the
same menu item should not oversell limited stock."

Single `@Transactional` service method:

1. Lock every requested `MenuItem` row with `SELECT ... FOR UPDATE`, locking
   in a stable order (by item id) across multi-item orders, to avoid
   deadlocks between two orders that both reference the same two items in
   different orders.
2. Validate `stock >= requestedQuantity` for every item; if any item fails,
   abort the whole order (no partial decrement across items).
3. Decrement stock, insert `Order` (`PLACED`) + `OrderItem` rows.
4. Call a `PaymentGateway.charge(amount)` port; the simulated implementation
   always succeeds (no real gateway is in scope), but the call happens
   inside the same transaction, so if it ever threw, the whole transaction
   — stock decrement included — would roll back with it. Only on success is
   a `Payment` row inserted.
5. On commit, publish `OrderPlacedEvent` (consumed asynchronously, see §7).

## 6. Delivery-Partner Assignment — Contention

"Partner assignment should handle multiple partners contending for the same
order."

When an order reaches `ACCEPTED`, a `DeliveryAssignment` row is created with
status `OPEN`, visible to active delivery partners in the restaurant's city
via `GET /assignments/open?city=`.

Any eligible partner calls `POST /assignments/{id}/accept`. The service
issues a single conditional update:

```sql
UPDATE delivery_assignment
SET status = 'ACCEPTED', partner_id = :partnerId, accepted_at = now()
WHERE id = :id AND status = 'OPEN'
```

Exactly one concurrent caller affects 1 row and wins; every other concurrent
caller affects 0 rows and receives a 409 "already assigned." This avoids
explicit locking for the contention path and is directly testable by firing
N concurrent accept calls and asserting exactly one success.

Once accepted, only the assigned partner may drive
`ACCEPTED -> OUT_FOR_DELIVERY -> DELIVERED` on the order.

## 7. Asynchronous Status Fan-out

"Status updates should fan out asynchronously to customer, restaurant, and
delivery partner without blocking the calling flow."

Every meaningful state change publishes a domain event (`OrderPlacedEvent`,
`OrderStatusChangedEvent`, `AssignmentAcceptedEvent`, ...) via
`ApplicationEventPublisher` from inside the transactional service method.

A `@TransactionalEventListener(phase = AFTER_COMMIT)` handler, marked
`@Async`, picks up the event only after the DB transaction commits (so a
rolled-back change never generates a stale notification), and writes one
`Notification` row per interested party (customer, restaurant owner,
assigned delivery partner where applicable). "Sending" is simulated as a
persisted row plus a log line — no real email/SMS/push integration, which is
out of scope.

Running on Spring's `@Async` executor (a bounded `ThreadPoolTaskExecutor`)
means the triggering HTTP call (place order, accept, status update) returns
before fan-out completes. Each user can `GET /notifications` (their own
inbox) and `POST /notifications/{id}/read`.

## 8. Auth & RBAC

Spring Security, stateless JWT. `POST /auth/register` (customer /
delivery-partner self-signup) and `POST /auth/login` return a signed JWT
(HMAC, short expiry) embedding user id + role; admin and restaurant-owner
accounts are created by an admin. Every other endpoint requires
`Authorization: Bearer <token>`.

Role permissions (enforced via `@PreAuthorize`):

- **ADMIN** — manage cities; manage restaurants (create, assign owner);
  manage delivery partners (approve/deactivate).
- **RESTAURANT_OWNER** — manage menu items for their own restaurant(s) only;
  accept/reject orders for their own restaurant.
- **CUSTOMER** — browse restaurants/menus; place orders; track own orders;
  rate own delivered orders.
- **DELIVERY_PARTNER** — view open assignments in their city; accept
  assignments; update status for orders assigned to them.

Role-level checks alone don't stop cross-tenant access (e.g. one owner
editing another's menu), so ownership is additionally checked in the service
layer by comparing the authenticated principal's id against the resource's
owning id.

## 9. API Surface (high level)

- **Auth:** `POST /auth/register`, `POST /auth/login`
- **Admin:** `POST/GET/PUT /cities`, `POST/GET/PUT /admin/restaurants`,
  `POST/GET/PUT /admin/delivery-partners`
- **Restaurant Owner:** `POST/PUT/DELETE /restaurants/{id}/menu-items`,
  `GET /restaurants/{id}/orders`, `POST /orders/{id}/accept`,
  `POST /orders/{id}/reject`, `POST /orders/{id}/status` (e.g. mark
  `PREPARING`)
- **Customer:** `GET /restaurants?city=`, `GET /restaurants/{id}/menu`,
  `POST /orders`, `GET /orders/{id}`, `GET /orders` (own order history),
  `POST /orders/{id}/ratings`
- **Delivery Partner:** `GET /assignments/open?city=`,
  `POST /assignments/{id}/accept`, `POST /orders/{id}/status` (e.g. mark
  `OUT_FOR_DELIVERY`/`DELIVERED`)
- **Shared:** `GET /notifications`, `POST /notifications/{id}/read`

All mutating endpoints use Bean Validation (`@Valid`). Errors use a
consistent RFC-7807-style `ProblemDetail` body (status, error code, message,
timestamp).

## 10. Testing Approach

- **Unit tests:** service-layer logic with mocked repositories — state
  transition rules, RBAC ownership checks, notification event construction.
- **Integration tests:** `@SpringBootTest` + Testcontainers Postgres, real
  HTTP calls via MockMvc/WebTestClient — full order placement -> payment ->
  stock decrement flow, accept/reject, rating rollup.
- **Concurrency tests** (the most important for this brief):
  - N threads placing orders concurrently against a menu item with stock =
    K (K < N): assert exactly K orders succeed, the rest get a clean 409,
    and final stock is exactly 0 (never negative).
  - N delivery partners concurrently accepting the same assignment: assert
    exactly 1 succeeds.
- **Async assertions:** Awaitility, polling until expected `Notification`
  rows appear.

## 11. Assumptions

- A restaurant has exactly one owner; an owner may own multiple restaurants.
- A customer orders from one restaurant per order (no cross-restaurant
  cart in a single order).
- Delivery partners are matched by city only (no real geolocation/routing —
  out of scope as "distributed systems").
- Payment is fully simulated in-process; no real payment gateway
  integration.
- "Basic RBAC" is interpreted as role + resource-ownership checks, not
  fine-grained permission policies.
- Ratings are attached to the restaurant per delivered order (the
  requirement says "rate" with no separate delivery-partner target); no
  order cancellation, no restaurant open/close-hours gating, no
  delivery-partner availability toggle, and no idempotency-key handling —
  none of these are named in the requirement, so they're left out rather
  than added as unrequested scope.
