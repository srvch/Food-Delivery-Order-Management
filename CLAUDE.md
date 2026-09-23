# CLAUDE.md

Guidance for AI assistants (and humans) working in this repository.

## What this is

A Spring Boot backend for the "Food Delivery Order Management" take-home
assignment: multi-city restaurants, per-restaurant menu management, customer
ordering with concurrency-safe stock and simulated payment, restaurant
accept/reject, delivery-partner assignment with contention handling,
asynchronous status-fan-out notifications, and restaurant ratings.

Full design rationale:
`docs/superpowers/specs/2026-09-23-food-delivery-order-management-design.md`
Implementation plan (12 TDD tasks, executed via `superpowers`
subagent-driven-development):
`docs/superpowers/plans/2026-09-23-food-delivery-order-management.md`
Development ledger (every task's review outcome and every design ruling
made along the way):
`.superpowers/sdd/2026-09-23-food-delivery-order-management/progress.md`

## Build & test

- Build: `./mvnw clean package`
- Run all tests: `./mvnw test` (requires Docker running — every integration
  test uses Testcontainers to start a real PostgreSQL instance, so behavior
  is verified against real Postgres semantics, not an in-memory
  approximation)
- Run the app: `./mvnw spring-boot:run` (requires a local PostgreSQL at
  `jdbc:postgresql://localhost:5432/fooddelivery`, user/pass `fooddelivery`)
- A default admin account is seeded by Flyway migration `V2`:
  `admin@fooddelivery.com` / `Admin@123`

## Tech stack

Java 17, Spring Boot 3.3 (Web, Data JPA, Security, Validation), PostgreSQL,
Flyway (schema migrations), JJWT (stateless JWT auth), Lombok, JUnit 5,
Mockito, Testcontainers, Awaitility (async test assertions).

(The plan originally targeted Java 21; it was dropped to 17 mid-project
because Java 21 wasn't obtainable in the development environment — a cask
install needed sudo, and a Homebrew formula download stalled twice on a
slow connection. Java 17 is a fully supported Spring Boot 3.3 baseline and
nothing in the codebase needs a 21-only language feature.)

## Architecture

**Module layout** — package-by-feature, one Spring Boot module:

```
com.fooddelivery
├── city          Admin: cities
├── restaurant    Admin/Owner: restaurants, menu items
├── order         Customer: order placement, lifecycle, state machine
├── delivery      Partner: assignment offers, accept/status updates,
│                 delivery-partner profiles (self-registration + admin mgmt)
├── rating        Customer: post-delivery restaurant ratings
├── notification  Async fan-out: domain events -> persisted notifications
├── user          Accounts, roles, JWT auth
└── common        Shared exceptions, GlobalExceptionHandler, security/JWT,
                   async config
```

Each feature package owns its entity, repository, service, controller, and
DTOs, and is testable in isolation behind its service interface. Cross-package
dependencies go one way at a time (e.g. `order` -> `restaurant`,
`order` -> `delivery`, `user` -> `delivery`) — no circular service
dependencies.

**Schema** is owned entirely by Flyway migrations under
`src/main/resources/db/migration`. `spring.jpa.hibernate.ddl-auto=validate`
means JPA entities must match the migrations exactly — when changing the
schema, update the migration first, then the entity, never the reverse.

## Domain model

| Entity | Key fields | Notes |
|---|---|---|
| `User` | id, email, password (bcrypt), role enum, enabled | Single table + role enum (`ADMIN`, `RESTAURANT_OWNER`, `CUSTOMER`, `DELIVERY_PARTNER`); implements `UserDetails` directly. |
| `City` | id, name, active | Admin-managed. |
| `Restaurant` | id, city FK, owner FK, name, address, avgRating, ratingCount | An owner may own multiple restaurants (reused by email on creation). |
| `MenuItem` | id, restaurant FK, name, price, stockQuantity, available | Row-locked (`SELECT ... FOR UPDATE`) during order placement. |
| `Order` | id, customer FK, restaurant FK, status enum, totalAmount, timestamps | Status: `PLACED, ACCEPTED, PREPARING, OUT_FOR_DELIVERY, DELIVERED, REJECTED` (no `CANCELLED` — nothing in scope produces it). |
| `OrderItem` | id, order FK, menuItem FK, quantity, unitPriceAtOrder | Price is snapshotted at order time, protecting history from later menu price changes. |
| `Payment` | id, order FK (1:1), amount, createdAt | Represents a *successful* charge only — a declined charge throws and rolls back the whole order-placement transaction, so there's no durable failed-payment state to model (no `status` column). |
| `DeliveryPartnerProfile` | id, user FK, city FK, active | Self-registered partners start inactive; admin approves/deactivates. |
| `DeliveryAssignment` | id, order FK (1:1), status (`OPEN`/`ACCEPTED`), partner FK (nullable), offeredAt, acceptedAt | The contention point: a conditional `UPDATE`, not a lock. No `CANCELLED` state — order rejection only happens before `ACCEPTED`, i.e. before any assignment exists. |
| `Rating` | id, order FK (unique), rater FK, score (1-5), review, createdAt | Rates the restaurant only — the requirement says "rate" with no separate delivery-partner target. |
| `Notification` | id, recipient FK, order FK, message, read, createdAt | The persisted result of async fan-out. |

## Order lifecycle & state machine

```
PLACED --accept(owner)--> ACCEPTED --start-preparing(owner)--> PREPARING
                                                                   |
                                                out-for-delivery(partner)
                                                                   v
                                                           OUT_FOR_DELIVERY --deliver(partner)--> DELIVERED
PLACED --reject(owner)--> REJECTED
```

Owner and partner status changes share one `POST /orders/{id}/status`
endpoint; `OrderService.updateStatus` dispatches on the caller's role and
validates ownership/assignment against the *requested* target status, not
just whether the transition is legal in the abstract. All legal transitions
live in one `Map<OrderStatus, Set<OrderStatus>>` in `OrderService` — a
single place to read and extend the whole state machine.

## The two core concurrency guarantees

**1. Stock never oversells** (`OrderService.placeOrder`): every requested
`MenuItem` row is locked with `SELECT ... FOR UPDATE`, in a stable order
(sorted by id) to avoid deadlocks across multi-item orders, all inside one
`@Transactional` method. Stock is checked and decremented only after the
lock is held; a competing transaction blocks on the same rows until commit.
Proven by `OrderPlacementConcurrencyIT` (N concurrent HTTP requests against
K < N stock; asserts exactly K succeed, the rest get a clean 409, final
stock is exactly 0 — verified against a real Testcontainers Postgres, not
mocked).

**2. Payment atomicity**: stock decrement, order creation, and the
simulated payment charge (`PaymentGateway.charge`) happen in the same
transaction. `SimulatedPaymentGateway` always succeeds in this codebase (no
real gateway is in scope); `PaymentDeclinedException` is a `RuntimeException`
that would roll back everything if a gateway implementation ever threw it —
proven directly by `OrderPlacementIT` using a `@MockBean` to force a decline
and asserting stock is unchanged afterward.

**3. Delivery-partner assignment never double-assigns**
(`DeliveryAssignmentService.acceptAssignment`): accepting is a single
conditional native `UPDATE ... WHERE id = :id AND status = 'OPEN'`. Exactly
one concurrent caller affects 1 row and wins; every other caller affects 0
rows and gets a 409. No explicit locking needed for this path. Proven by
`DeliveryAssignmentConcurrencyIT` (N partners race to accept one
assignment; asserts exactly 1 succeeds).

## Async notification fan-out

Every meaningful state change (`OrderPlacedEvent`, `OrderStatusChangedEvent`,
`AssignmentAcceptedEvent`) is published via `ApplicationEventPublisher` from
inside the transactional service method. `NotificationEventListener`
consumes them via `@TransactionalEventListener(phase = AFTER_COMMIT)` +
`@Async("notificationExecutor")`, so a rolled-back change never generates a
stale notification, and the triggering HTTP call never waits on fan-out.
Proven by `NotificationFanOutIT` using Awaitility to poll (not sleep) until
the expected notifications appear.

## Auth & RBAC

Stateless JWT (Spring Security). `POST /auth/register` (customer /
delivery-partner self-signup only) and `POST /auth/login` issue a signed
JWT embedding user id + role. Every other endpoint requires
`Authorization: Bearer <token>` — **there is no anonymous access anywhere
except `/auth/**`**. This was a real bug found and fixed once already (a
`permitAll()` added for "browsing" GETs contradicted the spec and was
reverted) — never reintroduce it. Every test that hits a GET endpoint must
send a bearer token.

Role capabilities:
- **ADMIN** — manage cities, restaurants (create + assign/reuse owner),
  delivery partners (approve/deactivate).
- **RESTAURANT_OWNER** — manage their own restaurant's menu, accept/reject
  orders, mark orders `PREPARING`, view their restaurant's orders.
- **CUSTOMER** — browse, place orders, view/track their own orders, rate
  their own delivered orders.
- **DELIVERY_PARTNER** — view open assignments in their city, accept one,
  drive an assigned order from `PREPARING` through `DELIVERED`.

Role checks alone don't stop cross-tenant access, so ownership is
additionally checked in the service layer everywhere it matters (an owner
can't touch another restaurant's menu, a customer can't read another
customer's order — this exact IDOR was found and fixed on
`GET /orders/{id}` during Task 7's review).

## Conventions

- Package-by-feature (see Architecture above).
- Money is always `BigDecimal` / SQL `NUMERIC(10,2)`, never `double`/`float`.
- Errors are thrown as `NotFoundException` / `ConflictException` /
  `ForbiddenException` / `BadRequestException` / `PaymentDeclinedException`
  (see `common.exception`) and mapped centrally by `GlobalExceptionHandler`
  to RFC-7807 `ProblemDetail` responses — don't catch and translate
  exceptions in individual controllers.
- Testcontainers uses a shared *singleton* Postgres container across all
  `*IT` classes in one JVM run (declared as a static field in
  `AbstractIntegrationTest`) — a well-known Testcontainers/JUnit gotcha is
  that a naive per-class `@Container` field gets stopped after each test
  class; the singleton pattern avoids that. Because the container (and its
  data) is shared across IT classes, tests that assert on "all rows" or "the
  first row" of a table are fragile — filter by the specific email/id your
  test created instead of assuming array position or an empty table.
- Native `@Modifying` queries (e.g. the conditional-accept `UPDATE`) need
  `clearAutomatically = true`, or a same-transaction `findById` immediately
  after will return a stale cached entity from the persistence context.
- Scope is intentionally limited to exactly what the source requirement
  states — do not add order cancellation, restaurant open/close-hours
  gating, delivery-partner availability toggles, idempotency-key handling,
  or delivery-partner ratings. These were explicitly proposed as "nice to
  have" extras during design, then deliberately cut when the user asked to
  match the source PDF exactly — see the spec's Assumptions section and the
  ledger's early rulings for the reasoning.

## Skills used during development

This project was built end-to-end using Claude Code's `superpowers` skill
set, in this order:
- `brainstorming` — architectural-path requirements/design dialogue,
  producing the design spec.
- `writing-plans` — turned the approved spec into a 12-task TDD
  implementation plan.
- `subagent-driven-development` — executed the plan: one fresh implementer
  subagent per task, a task-scoped reviewer after each (spec compliance +
  code quality), a fix loop for real findings, and a final whole-branch
  review at the end. Every task's outcome, every reviewer finding, and
  every design ruling made along the way is recorded in the ledger at
  `.superpowers/sdd/2026-09-23-food-delivery-order-management/progress.md`.
