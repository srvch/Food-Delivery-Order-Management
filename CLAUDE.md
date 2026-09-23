# CLAUDE.md

Guidance for AI assistants (and humans) working in this repository.

## What this is

A Spring Boot backend for the "Food Delivery Order Management" take-home
assignment. Full design rationale lives in
`docs/superpowers/specs/2026-09-23-food-delivery-order-management-design.md`;
the implementation plan is in
`docs/superpowers/plans/2026-09-23-food-delivery-order-management.md`.

## Build & test

- Build: `./mvnw clean package`
- Run all tests: `./mvnw test` (requires Docker running — integration tests
  use Testcontainers to start a real PostgreSQL instance)
- Run the app: `./mvnw spring-boot:run` (requires a local PostgreSQL at
  `jdbc:postgresql://localhost:5432/fooddelivery`, user/pass `fooddelivery`)

## Conventions

- Package-by-feature: `user`, `city`, `restaurant`, `order`, `delivery`,
  `rating`, `notification`, `common`. Each feature package owns its entity,
  repository, service, controller, and DTOs.
- Schema is owned by Flyway migrations under
  `src/main/resources/db/migration`; `ddl-auto=validate` means entities must
  match the migrations exactly — update the migration first, then the entity.
- Money is always `BigDecimal` / SQL `NUMERIC(10,2)`.
- Errors are thrown as `NotFoundException` / `ConflictException` /
  `ForbiddenException` (see `common.exception`) and mapped centrally by
  `GlobalExceptionHandler` to `ProblemDetail` responses — don't catch and
  translate exceptions in individual controllers.
- Scope is intentionally limited to what's in the design spec — do not add
  cancellation, restaurant hours, partner availability toggles, idempotency
  keys, or delivery-partner ratings; these were explicitly cut during design
  review to match the source requirement exactly.

## Skills used during development

This project was built using Claude Code's `superpowers` skill set:
`brainstorming` (requirements/design), `writing-plans` (this plan),
`subagent-driven-development` / `executing-plans` (implementation),
`test-driven-development`, `systematic-debugging`,
`verification-before-completion`, `requesting-code-review` /
`receiving-code-review`, and `finishing-a-development-branch`.
