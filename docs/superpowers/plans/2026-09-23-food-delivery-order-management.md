# Food Delivery Order Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Spring Boot backend described in the design spec — multi-city restaurants/menus, customer ordering with concurrency-safe stock/payment, restaurant accept/reject, delivery-partner assignment with contention handling, async status-fan-out notifications, and restaurant ratings — with RBAC across four roles.

**Architecture:** Single Spring Boot module, package-by-feature (`user`, `city`, `restaurant`, `order`, `delivery`, `rating`, `notification`, `common`). PostgreSQL + Flyway-versioned schema, JWT auth, pessimistic row locks for stock, conditional-update contention for partner assignment, Spring `@Async` + `@TransactionalEventListener(AFTER_COMMIT)` for notification fan-out.

**Tech Stack:** Java 21, Maven, Spring Boot 3.3.x (web, data-jpa, security, validation), PostgreSQL, Flyway, JJWT, Lombok, JUnit 5, Mockito, Testcontainers (Postgres), Awaitility.

**Spec:** `docs/superpowers/specs/2026-09-23-food-delivery-order-management-design.md`

## Global Constraints

- Java 21, Maven, Spring Boot 3.3.x — exact versions pinned in Task 1's `pom.xml`.
- Persistence: PostgreSQL only; `spring.jpa.hibernate.ddl-auto=validate` — schema comes exclusively from Flyway migrations, entities must match exactly.
- Scope is strictly what the spec's §9 API Surface and §3 Domain Model list — no cancellation, no restaurant hours gating, no delivery-partner availability toggle, no idempotency-key handling, ratings are restaurant-only. Do not add anything beyond this plan's tasks.
- All mutating endpoints validated with Bean Validation (`@Valid`); all errors returned via a single `GlobalExceptionHandler` producing `ProblemDetail` bodies (Task 2).
- Every task must leave `mvn test` green before moving to the next task.
- Money fields are `BigDecimal` mapped to `NUMERIC(10,2)`, never `double`/`float`.
- Commit after every task (not every step) unless a step explicitly says to commit.

---

### Task 1: Project Scaffolding

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/fooddelivery/FoodDeliveryApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `src/test/resources/application-test.yml`
- Create: `.mvn/` wrapper files (via `mvn wrapper:wrapper`)
- Create: `CLAUDE.md`
- Modify: `README.md`

**Interfaces:**
- Produces: a runnable Spring Boot app (`FoodDeliveryApplication`), Maven build (`./mvnw test`, `./mvnw spring-boot:run`), and `application.yml` property keys later tasks read: `spring.datasource.*`, `spring.flyway.*`, `app.jwt.secret`, `app.jwt.expiration-ms`.

- [ ] **Step 1: Initialize the Maven project**

Create `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.4</version>
        <relativePath/>
    </parent>

    <groupId>com.fooddelivery</groupId>
    <artifactId>food-delivery-order-management</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>food-delivery-order-management</name>
    <description>Food Delivery Order Management System</description>

    <properties>
        <java.version>21</java.version>
        <jjwt.version>0.12.6</jjwt.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.awaitility</groupId>
            <artifactId>awaitility</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.testcontainers</groupId>
                <artifactId>testcontainers-bom</artifactId>
                <version>1.20.1</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Generate the Maven wrapper**

Run: `mvn -N wrapper:wrapper -Dmaven=3.9.9`
Expected: creates `mvnw`, `mvnw.cmd`, `.mvn/wrapper/*`.

- [ ] **Step 3: Create the application entry point**

Create `src/main/java/com/fooddelivery/FoodDeliveryApplication.java`:

```java
package com.fooddelivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class FoodDeliveryApplication {
    public static void main(String[] args) {
        SpringApplication.run(FoodDeliveryApplication.class, args);
    }
}
```

- [ ] **Step 4: Configure application properties**

Create `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: food-delivery-order-management
  datasource:
    url: jdbc:postgresql://localhost:5432/fooddelivery
    username: fooddelivery
    password: fooddelivery
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        format_sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration

app:
  jwt:
    secret: "change-me-to-a-long-random-base64-string-change-me-please"
    expiration-ms: 3600000
```

Create `src/test/resources/application-test.yml`:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true

app:
  jwt:
    secret: "test-only-secret-key-not-for-production-use-0123456789"
    expiration-ms: 3600000
```

- [ ] **Step 5: Verify the app builds and boots**

Run: `./mvnw -q clean package -DskipTests`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Write CLAUDE.md**

Create `CLAUDE.md`:

```markdown
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
```

- [ ] **Step 7: Update README.md**

Replace the placeholder README content (from the initial commit) with:

```markdown
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
```

- [ ] **Step 8: Commit**

```bash
git add pom.xml mvnw mvnw.cmd .mvn src/main/java/com/fooddelivery/FoodDeliveryApplication.java src/main/resources/application.yml src/test/resources/application-test.yml CLAUDE.md README.md .gitignore
git commit -m "Scaffold Spring Boot project"
```

---

### Task 2: Database Schema, Core Config, Security Skeleton, and Test Infrastructure

**Files:**
- Create: `src/main/resources/db/migration/V1__init_schema.sql`
- Create: `src/main/resources/db/migration/V2__seed_admin.sql`
- Create: `src/main/java/com/fooddelivery/common/exception/NotFoundException.java`
- Create: `src/main/java/com/fooddelivery/common/exception/ConflictException.java`
- Create: `src/main/java/com/fooddelivery/common/exception/ForbiddenException.java`
- Create: `src/main/java/com/fooddelivery/common/exception/GlobalExceptionHandler.java`
- Create: `src/main/java/com/fooddelivery/common/config/AsyncConfig.java`
- Create: `src/main/java/com/fooddelivery/common/config/SecurityConfig.java`
- Create: `src/main/java/com/fooddelivery/user/Role.java`
- Test: `src/test/java/com/fooddelivery/common/AbstractIntegrationTest.java`
- Test: `src/test/java/com/fooddelivery/FoodDeliveryApplicationTests.java`

**Interfaces:**
- Consumes: nothing (first domain-agnostic infra task).
- Produces: `Role` enum (`ADMIN`, `RESTAURANT_OWNER`, `CUSTOMER`, `DELIVERY_PARTNER`) used by every later task; `NotFoundException`, `ConflictException`, `ForbiddenException` (all `RuntimeException` with a single `String message` constructor) used by every service; `AbstractIntegrationTest` (annotated `@SpringBootTest(webEnvironment = RANDOM_PORT) @ActiveProfiles("test") @Testcontainers`, exposes a static `PostgreSQLContainer<?> POSTGRES` and autowires `TestRestTemplate restTemplate`) that every later integration test extends; the full DB schema (all tables in §3 of the spec).

- [ ] **Step 1: Write the schema migration**

Create `src/main/resources/db/migration/V1__init_schema.sql`:

```sql
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
```

- [ ] **Step 2: Seed a default admin user**

Create `src/main/resources/db/migration/V2__seed_admin.sql` (password is
`Admin@123`, bcrypt-hashed — verified with `htpasswd -nbBC 10`):

```sql
INSERT INTO users (email, password, role, enabled)
VALUES ('admin@fooddelivery.com', '$2y$10$J0ALZNHRhlLJ/Tw6/OTf0efdE45Gf.tVRHA3Gxp6f9h82l2Wa2f72', 'ADMIN', TRUE);
```

- [ ] **Step 3: Create the Role enum**

Create `src/main/java/com/fooddelivery/user/Role.java`:

```java
package com.fooddelivery.user;

public enum Role {
    ADMIN, RESTAURANT_OWNER, CUSTOMER, DELIVERY_PARTNER
}
```

- [ ] **Step 4: Create shared exception types**

Create `src/main/java/com/fooddelivery/common/exception/NotFoundException.java`:

```java
package com.fooddelivery.common.exception;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
```

Create `src/main/java/com/fooddelivery/common/exception/ConflictException.java`:

```java
package com.fooddelivery.common.exception;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
```

Create `src/main/java/com/fooddelivery/common/exception/ForbiddenException.java`:

```java
package com.fooddelivery.common.exception;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
```

- [ ] **Step 5: Create the global exception handler**

Create `src/main/java/com/fooddelivery/common/exception/GlobalExceptionHandler.java`:

```java
package com.fooddelivery.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex) {
        return problem(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Access is denied");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return problem(HttpStatus.BAD_REQUEST, message);
    }

    private ProblemDetail problem(HttpStatus status, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }
}
```

- [ ] **Step 6: Configure async executor**

Create `src/main/java/com/fooddelivery/common/config/AsyncConfig.java`:

```java
package com.fooddelivery.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notif-");
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 7: Create a minimal security config (locked down, no JWT filter yet)**

Create `src/main/java/com/fooddelivery/common/config/SecurityConfig.java`:

```java
package com.fooddelivery.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
```

(Task 3 will add the JWT filter and `AuthenticationManager`/`UserDetailsService`
beans; this step only establishes the stateless baseline and public `/auth/**`
so the app boots and Task 3's tests can hit `/auth/register`.)

- [ ] **Step 8: Create the shared Testcontainers base class**

Create `src/test/java/com/fooddelivery/common/AbstractIntegrationTest.java`:

```java
package com.fooddelivery.common;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("fooddelivery")
                    .withUsername("fooddelivery")
                    .withPassword("fooddelivery");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected TestRestTemplate restTemplate;
}
```

- [ ] **Step 9: Write a context-loads smoke test**

Create `src/test/java/com/fooddelivery/FoodDeliveryApplicationTests.java`:

```java
package com.fooddelivery;

import com.fooddelivery.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class FoodDeliveryApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 10: Run the smoke test (requires Docker running)**

Run: `./mvnw test -Dtest=FoodDeliveryApplicationTests`
Expected: PASS — confirms Flyway applies `V1`/`V2` cleanly against a real
Postgres container and the Spring context starts.

- [ ] **Step 11: Commit**

```bash
git add src/main/resources/db/migration src/main/java/com/fooddelivery/common src/main/java/com/fooddelivery/user/Role.java src/test/java/com/fooddelivery/common src/test/java/com/fooddelivery/FoodDeliveryApplicationTests.java
git commit -m "Add DB schema, core config, and Testcontainers test infrastructure"
```

---

### Task 3: User Entity, JWT, and Auth Endpoints

**Files:**
- Create: `src/main/java/com/fooddelivery/user/User.java`
- Create: `src/main/java/com/fooddelivery/user/UserRepository.java`
- Create: `src/main/java/com/fooddelivery/user/dto/RegisterRequest.java`
- Create: `src/main/java/com/fooddelivery/user/dto/LoginRequest.java`
- Create: `src/main/java/com/fooddelivery/user/dto/AuthResponse.java`
- Create: `src/main/java/com/fooddelivery/user/AuthService.java`
- Create: `src/main/java/com/fooddelivery/user/AuthController.java`
- Create: `src/main/java/com/fooddelivery/common/security/JwtService.java`
- Create: `src/main/java/com/fooddelivery/common/security/JwtAuthFilter.java`
- Create: `src/main/java/com/fooddelivery/common/security/CustomUserDetailsService.java`
- Modify: `src/main/java/com/fooddelivery/common/config/SecurityConfig.java`
- Modify: `src/main/java/com/fooddelivery/common/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/com/fooddelivery/user/AuthServiceTest.java`
- Test: `src/test/java/com/fooddelivery/user/AuthControllerIT.java`

**Interfaces:**
- Consumes: `Role` (Task 2), `NotFoundException`/`ConflictException`/`ForbiddenException`/`GlobalExceptionHandler` (Task 2), `AbstractIntegrationTest` (Task 2).
- Produces: `User implements UserDetails` (fields: `Long id`, `String email`, `String password`, `Role role`, `boolean enabled`, `Instant createdAt`) — every later task's entities reference `User` by FK; `UserRepository.findByEmail(String): Optional<User>` and `.existsByEmail(String): boolean` — used by Task 6's admin delivery-partner approval and anywhere ownership is checked; `JwtService.generateToken(User): String` and `.isTokenValid(String): boolean` — not directly consumed elsewhere but documents the auth contract; every authenticated request exposes `Authentication.getPrincipal()` as a `User` instance, which later controllers cast via `@AuthenticationPrincipal User user`.

- [ ] **Step 1: Write the failing unit test for registration**

Create `src/test/java/com/fooddelivery/user/AuthServiceTest.java`:

```java
package com.fooddelivery.user;

import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.ForbiddenException;
import com.fooddelivery.common.security.JwtService;
import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @InjectMocks private AuthService authService;

    @Test
    void registerCreatesCustomerAndReturnsToken() {
        var request = new RegisterRequest("alice@example.com", "password123", Role.CUSTOMER, null);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("token-123");

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("token-123");
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void registerRejectsAdminRole() {
        var request = new RegisterRequest("bob@example.com", "password123", Role.ADMIN, null);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        var request = new RegisterRequest("alice@example.com", "password123", Role.CUSTOMER, null);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class);
    }
}
```

- [ ] **Step 2: Run it to confirm it fails to compile (types don't exist yet)**

Run: `./mvnw test -Dtest=AuthServiceTest`
Expected: COMPILATION ERROR — `Role`, `User`, `UserRepository`, `AuthService`, etc. under `user` package don't exist yet (only `Role` exists from Task 2).

- [ ] **Step 3: Create the User entity**

Create `src/main/java/com/fooddelivery/user/User.java`:

```java
package com.fooddelivery.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
```

- [ ] **Step 4: Create the repository**

Create `src/main/java/com/fooddelivery/user/UserRepository.java`:

```java
package com.fooddelivery.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

- [ ] **Step 5: Create the DTOs**

Create `src/main/java/com/fooddelivery/user/dto/RegisterRequest.java`:

```java
package com.fooddelivery.user.dto;

import com.fooddelivery.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotNull Role role,
        Long cityId
) {
}
```

(`cityId` is unused until Task 6, which needs it to create a
`DeliveryPartnerProfile` for `DELIVERY_PARTNER` registrations — declared now
so the wire contract doesn't change later.)

Create `src/main/java/com/fooddelivery/user/dto/LoginRequest.java`:

```java
package com.fooddelivery.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password
) {
}
```

Create `src/main/java/com/fooddelivery/user/dto/AuthResponse.java`:

```java
package com.fooddelivery.user.dto;

import com.fooddelivery.user.Role;

public record AuthResponse(String token, Long userId, Role role) {
}
```

- [ ] **Step 6: Create JwtService**

Create `src/main/java/com/fooddelivery/common/security/JwtService.java`:

```java
package com.fooddelivery.common.security;

import com.fooddelivery.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    public String extractUsername(String token) {
        return parse(token).getPayload().getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }
}
```

- [ ] **Step 7: Create CustomUserDetailsService**

Create `src/main/java/com/fooddelivery/common/security/CustomUserDetailsService.java`:

```java
package com.fooddelivery.common.security;

import com.fooddelivery.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
```

- [ ] **Step 8: Create the JWT filter**

Create `src/main/java/com/fooddelivery/common/security/JwtAuthFilter.java`:

```java
package com.fooddelivery.common.security;

import com.fooddelivery.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtService.isTokenValid(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
                String email = jwtService.extractUsername(token);
                userRepository.findByEmail(email).ifPresent(user -> {
                    var authToken = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                });
            }
        }
        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 9: Create AuthService**

Create `src/main/java/com/fooddelivery/user/AuthService.java`:

```java
package com.fooddelivery.user;

import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.ForbiddenException;
import com.fooddelivery.common.security.JwtService;
import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.LoginRequest;
import com.fooddelivery.user.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.role() != Role.CUSTOMER && request.role() != Role.DELIVERY_PARTNER) {
            throw new ForbiddenException(
                    "Self-registration is only allowed for CUSTOMER or DELIVERY_PARTNER roles");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered: " + request.email());
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user = userRepository.save(user);
        return new AuthResponse(jwtService.generateToken(user), user.getId(), user.getRole());
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalStateException("Authenticated user vanished: " + request.email()));
        return new AuthResponse(jwtService.generateToken(user), user.getId(), user.getRole());
    }
}
```

- [ ] **Step 10: Create AuthController**

Create `src/main/java/com/fooddelivery/user/AuthController.java`:

```java
package com.fooddelivery.user;

import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.LoginRequest;
import com.fooddelivery.user.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
```

- [ ] **Step 11: Wire JWT filter and AuthenticationManager into SecurityConfig**

Modify `src/main/java/com/fooddelivery/common/config/SecurityConfig.java` —
replace its contents with:

```java
package com.fooddelivery.common.config;

import com.fooddelivery.common.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

- [ ] **Step 12: Add an auth-failure handler to GlobalExceptionHandler**

Modify `src/main/java/com/fooddelivery/common/exception/GlobalExceptionHandler.java`
— add this method and its import (`org.springframework.security.core.AuthenticationException`):

```java
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ProblemDetail handleAuthentication(org.springframework.security.core.AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
```

- [ ] **Step 13: Run the unit test**

Run: `./mvnw test -Dtest=AuthServiceTest`
Expected: PASS (3 tests).

- [ ] **Step 14: Write the integration test**

Create `src/test/java/com/fooddelivery/user/AuthControllerIT.java`:

```java
package com.fooddelivery.user;

import com.fooddelivery.common.AbstractIntegrationTest;
import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.LoginRequest;
import com.fooddelivery.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerIT extends AbstractIntegrationTest {

    @Test
    void registerCustomerReturnsToken() {
        var request = new RegisterRequest("customer1@example.com", "password123", Role.CUSTOMER, null);

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity("/auth/register", request, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().token()).isNotBlank();
        assertThat(response.getBody().role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void registerAdminIsForbidden() {
        var request = new RegisterRequest("wannabe-admin@example.com", "password123", Role.ADMIN, null);

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void loginWithSeededAdminSucceeds() {
        var request = new LoginRequest("admin@fooddelivery.com", "Admin@123");

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity("/auth/login", request, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void loginWithWrongPasswordReturns401() {
        var request = new LoginRequest("admin@fooddelivery.com", "wrong-password");

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
```

- [ ] **Step 15: Run all tests (requires Docker running)**

Run: `./mvnw test`
Expected: PASS — all Task 1-3 tests green.

- [ ] **Step 16: Commit**

```bash
git add src/main/java/com/fooddelivery/user src/main/java/com/fooddelivery/common src/test/java/com/fooddelivery/user
git commit -m "Add user auth: registration, login, JWT filter"
```

---

### Task 4: City Management (Admin)

**Files:**
- Create: `src/main/java/com/fooddelivery/city/City.java`
- Create: `src/main/java/com/fooddelivery/city/CityRepository.java`
- Create: `src/main/java/com/fooddelivery/city/dto/CityRequest.java`
- Create: `src/main/java/com/fooddelivery/city/dto/CityResponse.java`
- Create: `src/main/java/com/fooddelivery/city/CityService.java`
- Create: `src/main/java/com/fooddelivery/city/CityController.java`
- Test: `src/test/java/com/fooddelivery/city/CityServiceTest.java`
- Test: `src/test/java/com/fooddelivery/city/CityControllerIT.java`

**Interfaces:**
- Consumes: `NotFoundException`, `ConflictException` (Task 2); auth from Task 3 (integration test logs in as the seeded admin to get a bearer token).
- Produces: `City` entity (`Long id`, `String name`, `boolean active`) — Task 5 (`Restaurant.city` FK) and Task 6 (`DeliveryPartnerProfile.city` FK) both reference it; `CityRepository extends JpaRepository<City, Long>` with `findByNameIgnoreCase(String): Optional<City>`; `CityResponse(Long id, String name, boolean active)`.

- [ ] **Step 1: Write the failing unit test**

Create `src/test/java/com/fooddelivery/city/CityServiceTest.java`:

```java
package com.fooddelivery.city;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CityServiceTest {

    @Mock private CityRepository cityRepository;
    @InjectMocks private CityService cityService;

    @Test
    void createCityRejectsDuplicateName() {
        when(cityRepository.findByNameIgnoreCase("Bangalore")).thenReturn(Optional.of(new City()));

        assertThatThrownBy(() -> cityService.createCity(new CityRequest("Bangalore", true)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createCitySucceeds() {
        when(cityRepository.findByNameIgnoreCase("Pune")).thenReturn(Optional.empty());
        when(cityRepository.save(any(City.class))).thenAnswer(inv -> {
            City c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        CityResponse response = cityService.createCity(new CityRequest("Pune", true));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Pune");
    }
}
```

- [ ] **Step 2: Run to confirm failure**

Run: `./mvnw test -Dtest=CityServiceTest`
Expected: COMPILATION ERROR (`City`, `CityRepository`, `CityService`, `dto.*` don't exist).

- [ ] **Step 3: Create the entity, repository, and DTOs**

Create `src/main/java/com/fooddelivery/city/City.java`:

```java
package com.fooddelivery.city;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cities")
@Getter
@Setter
@NoArgsConstructor
public class City {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private boolean active = true;
}
```

Create `src/main/java/com/fooddelivery/city/CityRepository.java`:

```java
package com.fooddelivery.city;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CityRepository extends JpaRepository<City, Long> {
    Optional<City> findByNameIgnoreCase(String name);
}
```

Create `src/main/java/com/fooddelivery/city/dto/CityRequest.java`:

```java
package com.fooddelivery.city.dto;

import jakarta.validation.constraints.NotBlank;

public record CityRequest(@NotBlank String name, boolean active) {
}
```

Create `src/main/java/com/fooddelivery/city/dto/CityResponse.java`:

```java
package com.fooddelivery.city.dto;

public record CityResponse(Long id, String name, boolean active) {
}
```

- [ ] **Step 4: Create CityService**

Create `src/main/java/com/fooddelivery/city/CityService.java`:

```java
package com.fooddelivery.city;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CityService {

    private final CityRepository cityRepository;

    @Transactional
    public CityResponse createCity(CityRequest request) {
        cityRepository.findByNameIgnoreCase(request.name()).ifPresent(c -> {
            throw new ConflictException("City already exists: " + request.name());
        });
        City city = new City();
        city.setName(request.name());
        city.setActive(request.active());
        return toResponse(cityRepository.save(city));
    }

    public List<CityResponse> listCities() {
        return cityRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public CityResponse updateCity(Long id, CityRequest request) {
        City city = cityRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("City not found: " + id));
        city.setName(request.name());
        city.setActive(request.active());
        return toResponse(city);
    }

    private CityResponse toResponse(City city) {
        return new CityResponse(city.getId(), city.getName(), city.isActive());
    }
}
```

- [ ] **Step 5: Run the unit test**

Run: `./mvnw test -Dtest=CityServiceTest`
Expected: PASS.

- [ ] **Step 6: Create CityController**

Create `src/main/java/com/fooddelivery/city/CityController.java`:

```java
package com.fooddelivery.city;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cities")
@RequiredArgsConstructor
public class CityController {

    private final CityService cityService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CityResponse> create(@Valid @RequestBody CityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cityService.createCity(request));
    }

    @GetMapping
    public List<CityResponse> list() {
        return cityService.listCities();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CityResponse update(@PathVariable Long id, @Valid @RequestBody CityRequest request) {
        return cityService.updateCity(id, request);
    }
}
```

- [ ] **Step 7: Enable method security**

Modify `src/main/java/com/fooddelivery/common/config/SecurityConfig.java` —
add `@EnableMethodSecurity` to the class annotations (import
`org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity`)
so `@PreAuthorize` is honored:

```java
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
```

- [ ] **Step 8: Write the integration test**

Create `src/test/java/com/fooddelivery/city/CityControllerIT.java`:

```java
package com.fooddelivery.city;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.AbstractIntegrationTest;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.LoginRequest;
import com.fooddelivery.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

class CityControllerIT extends AbstractIntegrationTest {

    private String adminToken() {
        var login = new LoginRequest("admin@fooddelivery.com", "Admin@123");
        return restTemplate.postForEntity("/auth/login", login, AuthResponse.class).getBody().token();
    }

    private HttpEntity<CityRequest> authed(CityRequest body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void adminCanCreateCity() {
        ResponseEntity<CityResponse> response = restTemplate.exchange(
                "/cities", HttpMethod.POST, authed(new CityRequest("Mumbai", true), adminToken()), CityResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().name()).isEqualTo("Mumbai");
    }

    @Test
    void nonAdminCannotCreateCity() {
        var register = new RegisterRequest("cust-city@example.com", "password123", Role.CUSTOMER, null);
        String token = restTemplate.postForEntity("/auth/register", register, AuthResponse.class).getBody().token();

        ResponseEntity<String> response = restTemplate.exchange(
                "/cities", HttpMethod.POST, authed(new CityRequest("Delhi", true), token), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
```

- [ ] **Step 9: Run all tests (requires Docker running)**

Run: `./mvnw test`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/fooddelivery/city src/main/java/com/fooddelivery/common/config/SecurityConfig.java src/test/java/com/fooddelivery/city
git commit -m "Add city management for admins"
```

---

### Task 5: Restaurant and Menu Item Management

**Files:**
- Create: `src/main/java/com/fooddelivery/restaurant/Restaurant.java`
- Create: `src/main/java/com/fooddelivery/restaurant/RestaurantRepository.java`
- Create: `src/main/java/com/fooddelivery/restaurant/MenuItem.java`
- Create: `src/main/java/com/fooddelivery/restaurant/MenuItemRepository.java`
- Create: `src/main/java/com/fooddelivery/restaurant/dto/AdminCreateRestaurantRequest.java`
- Create: `src/main/java/com/fooddelivery/restaurant/dto/RestaurantUpdateRequest.java`
- Create: `src/main/java/com/fooddelivery/restaurant/dto/RestaurantResponse.java`
- Create: `src/main/java/com/fooddelivery/restaurant/dto/MenuItemRequest.java`
- Create: `src/main/java/com/fooddelivery/restaurant/dto/MenuItemResponse.java`
- Create: `src/main/java/com/fooddelivery/restaurant/RestaurantService.java`
- Create: `src/main/java/com/fooddelivery/restaurant/MenuItemService.java`
- Create: `src/main/java/com/fooddelivery/restaurant/RestaurantController.java`
- Create: `src/main/java/com/fooddelivery/restaurant/MenuItemController.java`
- Test: `src/test/java/com/fooddelivery/restaurant/RestaurantServiceTest.java`
- Test: `src/test/java/com/fooddelivery/restaurant/MenuItemServiceTest.java`
- Test: `src/test/java/com/fooddelivery/restaurant/RestaurantMenuControllerIT.java`

**Interfaces:**
- Consumes: `City`/`CityRepository` (Task 4); `User`/`UserRepository`/`Role` (Task 3); `NotFoundException`/`ConflictException`/`ForbiddenException` (Task 2).
- Produces: `Restaurant` (`Long id`, `City city`, `User owner`, `String name`, `String address`, `BigDecimal avgRating`, `int ratingCount`) — Task 7 (`Order.restaurant`), Task 9 (city lookup for assignment eligibility), and Task 11 (rating rollup) all depend on it; `RestaurantRepository.findById`; `RestaurantService.getRestaurantEntity(Long): Restaurant` and `.getOwnedRestaurantEntity(Long restaurantId, Long ownerId): Restaurant` (throws `NotFoundException`/`ForbiddenException`) — the ownership-check pattern every later owner-scoped operation reuses; `MenuItem` (`Long id`, `Restaurant restaurant`, `String name`, `BigDecimal price`, `int stockQuantity`, `boolean available`) and `MenuItemRepository.findByIdAndRestaurantId(Long, Long): Optional<MenuItem>` — Task 7's order placement locks and reads these rows directly via a new repository method it adds (`findAllByIdInForUpdate`, see Task 7).

- [ ] **Step 1: Write the failing unit test for restaurant creation**

Create `src/test/java/com/fooddelivery/restaurant/RestaurantServiceTest.java`:

```java
package com.fooddelivery.restaurant;

import com.fooddelivery.city.City;
import com.fooddelivery.city.CityRepository;
import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.ForbiddenException;
import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.restaurant.dto.AdminCreateRestaurantRequest;
import com.fooddelivery.restaurant.dto.RestaurantResponse;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.User;
import com.fooddelivery.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock private RestaurantRepository restaurantRepository;
    @Mock private CityRepository cityRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private RestaurantService restaurantService;

    @Test
    void createRestaurantCreatesNewOwnerWhenEmailUnused() {
        City city = new City();
        city.setId(1L);
        city.setName("Pune");
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city));
        when(userRepository.findByEmail("owner1@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> {
            Restaurant r = inv.getArgument(0);
            r.setId(100L);
            return r;
        });

        var request = new AdminCreateRestaurantRequest(1L, "Tasty Bites", "MG Road", "owner1@example.com", "password123");
        RestaurantResponse response = restaurantService.createRestaurantWithOwner(request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.ownerId()).isEqualTo(10L);
    }

    @Test
    void createRestaurantRejectsEmailBelongingToNonOwner() {
        City city = new City();
        city.setId(1L);
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city));
        User existing = new User();
        existing.setRole(Role.CUSTOMER);
        when(userRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(existing));

        var request = new AdminCreateRestaurantRequest(1L, "Tasty Bites", "MG Road", "taken@example.com", "password123");

        assertThatThrownBy(() -> restaurantService.createRestaurantWithOwner(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createRestaurantRejectsUnknownCity() {
        when(cityRepository.findById(99L)).thenReturn(Optional.empty());

        var request = new AdminCreateRestaurantRequest(99L, "Tasty Bites", "MG Road", "owner2@example.com", "password123");

        assertThatThrownBy(() -> restaurantService.createRestaurantWithOwner(request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getOwnedRestaurantEntityRejectsWrongOwner() {
        User owner = new User();
        owner.setId(1L);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        restaurant.setOwner(owner);
        when(restaurantRepository.findById(5L)).thenReturn(Optional.of(restaurant));

        assertThatThrownBy(() -> restaurantService.getOwnedRestaurantEntity(5L, 2L))
                .isInstanceOf(ForbiddenException.class);
    }
}
```

- [ ] **Step 2: Run to confirm failure**

Run: `./mvnw test -Dtest=RestaurantServiceTest`
Expected: COMPILATION ERROR — `Restaurant`, `RestaurantService`, etc. don't exist yet.

- [ ] **Step 3: Create the Restaurant entity and repository**

Create `src/main/java/com/fooddelivery/restaurant/Restaurant.java`:

```java
package com.fooddelivery.restaurant;

import com.fooddelivery.city.City;
import com.fooddelivery.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "restaurants")
@Getter
@Setter
@NoArgsConstructor
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "city_id")
    private City city;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(name = "avg_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
```

Create `src/main/java/com/fooddelivery/restaurant/RestaurantRepository.java`:

```java
package com.fooddelivery.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
    List<Restaurant> findByCityId(Long cityId);
}
```

- [ ] **Step 4: Create the MenuItem entity and repository**

Create `src/main/java/com/fooddelivery/restaurant/MenuItem.java`:

```java
package com.fooddelivery.restaurant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "menu_items")
@Getter
@Setter
@NoArgsConstructor
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(nullable = false)
    private boolean available = true;
}
```

Create `src/main/java/com/fooddelivery/restaurant/MenuItemRepository.java`:

```java
package com.fooddelivery.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByRestaurantId(Long restaurantId);
    Optional<MenuItem> findByIdAndRestaurantId(Long id, Long restaurantId);
}
```

- [ ] **Step 5: Create the DTOs**

Create `src/main/java/com/fooddelivery/restaurant/dto/AdminCreateRestaurantRequest.java`:

```java
package com.fooddelivery.restaurant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminCreateRestaurantRequest(
        @NotNull Long cityId,
        @NotBlank String name,
        @NotBlank String address,
        @Email @NotBlank String ownerEmail,
        @NotBlank @Size(min = 8, max = 100) String ownerPassword
) {
}
```

Create `src/main/java/com/fooddelivery/restaurant/dto/RestaurantUpdateRequest.java`:

```java
package com.fooddelivery.restaurant.dto;

import jakarta.validation.constraints.NotBlank;

public record RestaurantUpdateRequest(@NotBlank String name, @NotBlank String address) {
}
```

Create `src/main/java/com/fooddelivery/restaurant/dto/RestaurantResponse.java`:

```java
package com.fooddelivery.restaurant.dto;

import java.math.BigDecimal;

public record RestaurantResponse(
        Long id, Long cityId, String cityName, Long ownerId,
        String name, String address, BigDecimal avgRating, int ratingCount
) {
}
```

Create `src/main/java/com/fooddelivery/restaurant/dto/MenuItemRequest.java`:

```java
package com.fooddelivery.restaurant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record MenuItemRequest(
        @NotBlank String name,
        @NotNull @DecimalMin("0.01") BigDecimal price,
        @Min(0) int stockQuantity,
        boolean available
) {
}
```

Create `src/main/java/com/fooddelivery/restaurant/dto/MenuItemResponse.java`:

```java
package com.fooddelivery.restaurant.dto;

import java.math.BigDecimal;

public record MenuItemResponse(
        Long id, Long restaurantId, String name, BigDecimal price, int stockQuantity, boolean available
) {
}
```

- [ ] **Step 6: Create RestaurantService**

Create `src/main/java/com/fooddelivery/restaurant/RestaurantService.java`:

```java
package com.fooddelivery.restaurant;

import com.fooddelivery.city.City;
import com.fooddelivery.city.CityRepository;
import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.ForbiddenException;
import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.restaurant.dto.AdminCreateRestaurantRequest;
import com.fooddelivery.restaurant.dto.RestaurantResponse;
import com.fooddelivery.restaurant.dto.RestaurantUpdateRequest;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.User;
import com.fooddelivery.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final CityRepository cityRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public RestaurantResponse createRestaurantWithOwner(AdminCreateRestaurantRequest request) {
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> new NotFoundException("City not found: " + request.cityId()));

        User owner = userRepository.findByEmail(request.ownerEmail()).map(existing -> {
            if (existing.getRole() != Role.RESTAURANT_OWNER) {
                throw new ConflictException(
                        "Email belongs to an account that is not a restaurant owner: " + request.ownerEmail());
            }
            return existing;
        }).orElseGet(() -> {
            User newOwner = new User();
            newOwner.setEmail(request.ownerEmail());
            newOwner.setPassword(passwordEncoder.encode(request.ownerPassword()));
            newOwner.setRole(Role.RESTAURANT_OWNER);
            return userRepository.save(newOwner);
        });

        Restaurant restaurant = new Restaurant();
        restaurant.setCity(city);
        restaurant.setOwner(owner);
        restaurant.setName(request.name());
        restaurant.setAddress(request.address());
        return toResponse(restaurantRepository.save(restaurant));
    }

    public RestaurantResponse getRestaurant(Long id) {
        return toResponse(getRestaurantEntity(id));
    }

    public Restaurant getRestaurantEntity(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Restaurant not found: " + id));
    }

    public Restaurant getOwnedRestaurantEntity(Long restaurantId, Long ownerId) {
        Restaurant restaurant = getRestaurantEntity(restaurantId);
        if (!restaurant.getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("You do not own restaurant " + restaurantId);
        }
        return restaurant;
    }

    public List<RestaurantResponse> listRestaurants(Long cityId) {
        List<Restaurant> restaurants = cityId == null
                ? restaurantRepository.findAll()
                : restaurantRepository.findByCityId(cityId);
        return restaurants.stream().map(this::toResponse).toList();
    }

    @Transactional
    public RestaurantResponse updateRestaurant(Long id, RestaurantUpdateRequest request) {
        Restaurant restaurant = getRestaurantEntity(id);
        restaurant.setName(request.name());
        restaurant.setAddress(request.address());
        return toResponse(restaurant);
    }

    private RestaurantResponse toResponse(Restaurant restaurant) {
        return new RestaurantResponse(
                restaurant.getId(), restaurant.getCity().getId(), restaurant.getCity().getName(),
                restaurant.getOwner().getId(), restaurant.getName(), restaurant.getAddress(),
                restaurant.getAvgRating(), restaurant.getRatingCount());
    }
}
```

- [ ] **Step 7: Run the unit test**

Run: `./mvnw test -Dtest=RestaurantServiceTest`
Expected: PASS (4 tests).

- [ ] **Step 8: Write the failing unit test for menu items**

Create `src/test/java/com/fooddelivery/restaurant/MenuItemServiceTest.java`:

```java
package com.fooddelivery.restaurant;

import com.fooddelivery.common.exception.ForbiddenException;
import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.restaurant.dto.MenuItemRequest;
import com.fooddelivery.restaurant.dto.MenuItemResponse;
import com.fooddelivery.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuItemServiceTest {

    @Mock private MenuItemRepository menuItemRepository;
    @Mock private RestaurantService restaurantService;
    @InjectMocks private MenuItemService menuItemService;

    private Restaurant ownedRestaurant(Long ownerId) {
        User owner = new User();
        owner.setId(ownerId);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        restaurant.setOwner(owner);
        return restaurant;
    }

    @Test
    void addMenuItemSucceedsForOwner() {
        when(restaurantService.getOwnedRestaurantEntity(5L, 1L)).thenReturn(ownedRestaurant(1L));
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(inv -> {
            MenuItem m = inv.getArgument(0);
            m.setId(50L);
            return m;
        });

        var request = new MenuItemRequest("Paneer Tikka", new BigDecimal("199.00"), 10, true);
        MenuItemResponse response = menuItemService.addMenuItem(1L, 5L, request);

        assertThat(response.id()).isEqualTo(50L);
        assertThat(response.name()).isEqualTo("Paneer Tikka");
    }

    @Test
    void addMenuItemFailsForNonOwner() {
        when(restaurantService.getOwnedRestaurantEntity(5L, 2L))
                .thenThrow(new ForbiddenException("You do not own restaurant 5"));

        var request = new MenuItemRequest("Paneer Tikka", new BigDecimal("199.00"), 10, true);

        assertThatThrownBy(() -> menuItemService.addMenuItem(2L, 5L, request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateMenuItemRejectsItemFromDifferentRestaurant() {
        when(restaurantService.getOwnedRestaurantEntity(5L, 1L)).thenReturn(ownedRestaurant(1L));
        when(menuItemRepository.findByIdAndRestaurantId(50L, 5L)).thenReturn(Optional.empty());

        var request = new MenuItemRequest("Paneer Tikka", new BigDecimal("199.00"), 10, true);

        assertThatThrownBy(() -> menuItemService.updateMenuItem(1L, 5L, 50L, request))
                .isInstanceOf(NotFoundException.class);
    }
}
```

- [ ] **Step 9: Run to confirm failure, then create MenuItemService**

Run: `./mvnw test -Dtest=MenuItemServiceTest` — expect COMPILATION ERROR.

Create `src/main/java/com/fooddelivery/restaurant/MenuItemService.java`:

```java
package com.fooddelivery.restaurant;

import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.restaurant.dto.MenuItemRequest;
import com.fooddelivery.restaurant.dto.MenuItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final RestaurantService restaurantService;

    @Transactional
    public MenuItemResponse addMenuItem(Long ownerId, Long restaurantId, MenuItemRequest request) {
        Restaurant restaurant = restaurantService.getOwnedRestaurantEntity(restaurantId, ownerId);
        MenuItem item = new MenuItem();
        item.setRestaurant(restaurant);
        applyRequest(item, request);
        return toResponse(menuItemRepository.save(item));
    }

    @Transactional
    public MenuItemResponse updateMenuItem(Long ownerId, Long restaurantId, Long itemId, MenuItemRequest request) {
        restaurantService.getOwnedRestaurantEntity(restaurantId, ownerId);
        MenuItem item = menuItemRepository.findByIdAndRestaurantId(itemId, restaurantId)
                .orElseThrow(() -> new NotFoundException("Menu item not found: " + itemId));
        applyRequest(item, request);
        return toResponse(item);
    }

    @Transactional
    public void deleteMenuItem(Long ownerId, Long restaurantId, Long itemId) {
        restaurantService.getOwnedRestaurantEntity(restaurantId, ownerId);
        MenuItem item = menuItemRepository.findByIdAndRestaurantId(itemId, restaurantId)
                .orElseThrow(() -> new NotFoundException("Menu item not found: " + itemId));
        menuItemRepository.delete(item);
    }

    public List<MenuItemResponse> listMenu(Long restaurantId) {
        restaurantService.getRestaurantEntity(restaurantId);
        return menuItemRepository.findByRestaurantId(restaurantId).stream().map(this::toResponse).toList();
    }

    private void applyRequest(MenuItem item, MenuItemRequest request) {
        item.setName(request.name());
        item.setPrice(request.price());
        item.setStockQuantity(request.stockQuantity());
        item.setAvailable(request.available());
    }

    private MenuItemResponse toResponse(MenuItem item) {
        return new MenuItemResponse(item.getId(), item.getRestaurant().getId(), item.getName(),
                item.getPrice(), item.getStockQuantity(), item.isAvailable());
    }
}
```

- [ ] **Step 10: Run the unit test**

Run: `./mvnw test -Dtest=MenuItemServiceTest`
Expected: PASS (3 tests).

- [ ] **Step 11: Create the controllers**

Create `src/main/java/com/fooddelivery/restaurant/RestaurantController.java`:

```java
package com.fooddelivery.restaurant;

import com.fooddelivery.restaurant.dto.AdminCreateRestaurantRequest;
import com.fooddelivery.restaurant.dto.RestaurantResponse;
import com.fooddelivery.restaurant.dto.RestaurantUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;

    @PostMapping("/admin/restaurants")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestaurantResponse> create(@Valid @RequestBody AdminCreateRestaurantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(restaurantService.createRestaurantWithOwner(request));
    }

    @GetMapping("/admin/restaurants")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RestaurantResponse> listForAdmin() {
        return restaurantService.listRestaurants(null);
    }

    @PutMapping("/admin/restaurants/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RestaurantResponse update(@PathVariable Long id, @Valid @RequestBody RestaurantUpdateRequest request) {
        return restaurantService.updateRestaurant(id, request);
    }

    @GetMapping("/restaurants")
    public List<RestaurantResponse> list(@RequestParam(required = false) Long cityId) {
        return restaurantService.listRestaurants(cityId);
    }

    @GetMapping("/restaurants/{id}")
    public RestaurantResponse get(@PathVariable Long id) {
        return restaurantService.getRestaurant(id);
    }
}
```

Create `src/main/java/com/fooddelivery/restaurant/MenuItemController.java`:

```java
package com.fooddelivery.restaurant;

import com.fooddelivery.restaurant.dto.MenuItemRequest;
import com.fooddelivery.restaurant.dto.MenuItemResponse;
import com.fooddelivery.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/restaurants/{restaurantId}")
@RequiredArgsConstructor
public class MenuItemController {

    private final MenuItemService menuItemService;

    @PostMapping("/menu-items")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public ResponseEntity<MenuItemResponse> add(@PathVariable Long restaurantId,
                                                 @AuthenticationPrincipal User owner,
                                                 @Valid @RequestBody MenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(menuItemService.addMenuItem(owner.getId(), restaurantId, request));
    }

    @PutMapping("/menu-items/{itemId}")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public MenuItemResponse update(@PathVariable Long restaurantId, @PathVariable Long itemId,
                                   @AuthenticationPrincipal User owner,
                                   @Valid @RequestBody MenuItemRequest request) {
        return menuItemService.updateMenuItem(owner.getId(), restaurantId, itemId, request);
    }

    @DeleteMapping("/menu-items/{itemId}")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public ResponseEntity<Void> delete(@PathVariable Long restaurantId, @PathVariable Long itemId,
                                        @AuthenticationPrincipal User owner) {
        menuItemService.deleteMenuItem(owner.getId(), restaurantId, itemId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/menu")
    public List<MenuItemResponse> listMenu(@PathVariable Long restaurantId) {
        return menuItemService.listMenu(restaurantId);
    }
}
```

- [ ] **Step 12: Write the integration test**

Create `src/test/java/com/fooddelivery/restaurant/RestaurantMenuControllerIT.java`:

```java
package com.fooddelivery.restaurant;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.AbstractIntegrationTest;
import com.fooddelivery.restaurant.dto.*;
import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RestaurantMenuControllerIT extends AbstractIntegrationTest {

    private String adminToken() {
        var login = new LoginRequest("admin@fooddelivery.com", "Admin@123");
        return restTemplate.postForEntity("/auth/login", login, AuthResponse.class).getBody().token();
    }

    private <T> HttpEntity<T> authed(T body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private Long createCity(String name, String token) {
        var response = restTemplate.exchange("/cities", HttpMethod.POST,
                authed(new CityRequest(name, true), token), CityResponse.class);
        return response.getBody().id();
    }

    @Test
    void ownerCanManageOwnMenuButNotAnotherRestaurant() {
        String admin = adminToken();
        Long cityId = createCity("Chennai", admin);

        var createReq = new AdminCreateRestaurantRequest(cityId, "Spice Hub", "Anna Salai",
                "owner-a@example.com", "password123");
        RestaurantResponse restaurantA = restTemplate.exchange("/admin/restaurants", HttpMethod.POST,
                authed(createReq, admin), RestaurantResponse.class).getBody();

        var createReqB = new AdminCreateRestaurantRequest(cityId, "Curry Corner", "T Nagar",
                "owner-b@example.com", "password123");
        RestaurantResponse restaurantB = restTemplate.exchange("/admin/restaurants", HttpMethod.POST,
                authed(createReqB, admin), RestaurantResponse.class).getBody();

        String ownerAToken = restTemplate.postForEntity("/auth/login",
                new LoginRequest("owner-a@example.com", "password123"), AuthResponse.class).getBody().token();
        String ownerBToken = restTemplate.postForEntity("/auth/login",
                new LoginRequest("owner-b@example.com", "password123"), AuthResponse.class).getBody().token();

        var menuItemReq = new MenuItemRequest("Masala Dosa", new BigDecimal("80.00"), 20, true);
        ResponseEntity<MenuItemResponse> addOwn = restTemplate.exchange(
                "/restaurants/" + restaurantA.id() + "/menu-items", HttpMethod.POST,
                authed(menuItemReq, ownerAToken), MenuItemResponse.class);
        assertThat(addOwn.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> addToOthers = restTemplate.exchange(
                "/restaurants/" + restaurantB.id() + "/menu-items", HttpMethod.POST,
                authed(menuItemReq, ownerAToken), String.class);
        assertThat(addToOthers.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<MenuItemResponse[]> menu = restTemplate.getForEntity(
                "/restaurants/" + restaurantA.id() + "/menu", MenuItemResponse[].class);
        assertThat(menu.getBody()).hasSize(1);
        assertThat(menu.getBody()[0].name()).isEqualTo("Masala Dosa");
    }
}
```

- [ ] **Step 13: Run all tests (requires Docker running)**

Run: `./mvnw test`
Expected: PASS.

- [ ] **Step 14: Commit**

```bash
git add src/main/java/com/fooddelivery/restaurant src/test/java/com/fooddelivery/restaurant
git commit -m "Add restaurant and menu item management"
```

---

### Task 6: Delivery Partner Profiles (Self-Registration + Admin Management)

**Files:**
- Create: `src/main/java/com/fooddelivery/delivery/DeliveryPartnerProfile.java`
- Create: `src/main/java/com/fooddelivery/delivery/DeliveryPartnerProfileRepository.java`
- Create: `src/main/java/com/fooddelivery/delivery/dto/AdminCreateDeliveryPartnerRequest.java`
- Create: `src/main/java/com/fooddelivery/delivery/dto/DeliveryPartnerUpdateRequest.java`
- Create: `src/main/java/com/fooddelivery/delivery/dto/DeliveryPartnerResponse.java`
- Create: `src/main/java/com/fooddelivery/delivery/DeliveryPartnerService.java`
- Create: `src/main/java/com/fooddelivery/delivery/DeliveryPartnerController.java`
- Create: `src/main/java/com/fooddelivery/common/exception/BadRequestException.java`
- Modify: `src/main/java/com/fooddelivery/common/exception/GlobalExceptionHandler.java`
- Modify: `src/main/java/com/fooddelivery/user/AuthService.java`
- Modify: `src/test/java/com/fooddelivery/user/AuthServiceTest.java`
- Test: `src/test/java/com/fooddelivery/delivery/DeliveryPartnerServiceTest.java`
- Test: `src/test/java/com/fooddelivery/delivery/DeliveryPartnerControllerIT.java`

**Interfaces:**
- Consumes: `City`/`CityRepository` (Task 4); `User`/`UserRepository`/`Role` (Task 3); `RegisterRequest.cityId()` (Task 3, previously unused); `NotFoundException`/`ConflictException`/`ForbiddenException` (Task 2).
- Produces: `DeliveryPartnerProfile` (`Long id`, `User user`, `City city`, `boolean active`); `DeliveryPartnerProfileRepository.findByUserId(Long): Optional<DeliveryPartnerProfile>` and `.findByCityIdAndActiveTrue(Long): List<DeliveryPartnerProfile>` — Task 9's assignment eligibility query depends directly on the latter; `BadRequestException` (new common exception, `RuntimeException`, mapped to 400) — used whenever a request is well-formed JSON but fails a cross-field business rule that Bean Validation annotations can't express (here: `cityId` required only when `role == DELIVERY_PARTNER`).

- [ ] **Step 1: Add BadRequestException and wire it into the handler**

Create `src/main/java/com/fooddelivery/common/exception/BadRequestException.java`:

```java
package com.fooddelivery.common.exception;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
```

Modify `src/main/java/com/fooddelivery/common/exception/GlobalExceptionHandler.java` —
add:

```java
    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
```

- [ ] **Step 2: Write the failing unit test for the profile service**

Create `src/test/java/com/fooddelivery/delivery/DeliveryPartnerServiceTest.java`:

```java
package com.fooddelivery.delivery;

import com.fooddelivery.city.City;
import com.fooddelivery.city.CityRepository;
import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.delivery.dto.AdminCreateDeliveryPartnerRequest;
import com.fooddelivery.delivery.dto.DeliveryPartnerResponse;
import com.fooddelivery.delivery.dto.DeliveryPartnerUpdateRequest;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.User;
import com.fooddelivery.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryPartnerServiceTest {

    @Mock private DeliveryPartnerProfileRepository profileRepository;
    @Mock private CityRepository cityRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private DeliveryPartnerService deliveryPartnerService;

    @Test
    void adminCreatePartnerActivatesImmediately() {
        City city = new City();
        city.setId(1L);
        city.setName("Pune");
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city));
        when(userRepository.existsByEmail("partner1@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(20L);
            return u;
        });
        when(profileRepository.save(any(DeliveryPartnerProfile.class))).thenAnswer(inv -> {
            DeliveryPartnerProfile p = inv.getArgument(0);
            p.setId(200L);
            return p;
        });

        var request = new AdminCreateDeliveryPartnerRequest(1L, "partner1@example.com", "password123");
        DeliveryPartnerResponse response = deliveryPartnerService.createPartner(request);

        assertThat(response.active()).isTrue();
        assertThat(response.cityId()).isEqualTo(1L);
    }

    @Test
    void registerProfileForSelfStartsInactive() {
        City city = new City();
        city.setId(2L);
        when(cityRepository.findById(2L)).thenReturn(Optional.of(city));
        User user = new User();
        user.setId(30L);
        when(profileRepository.save(any(DeliveryPartnerProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        DeliveryPartnerProfile profile = deliveryPartnerService.registerProfileForSelf(user, 2L);

        assertThat(profile.isActive()).isFalse();
        assertThat(profile.getCity()).isEqualTo(city);
    }

    @Test
    void registerProfileForSelfRejectsUnknownCity() {
        when(cityRepository.findById(99L)).thenReturn(Optional.empty());
        User user = new User();
        user.setId(31L);

        assertThatThrownBy(() -> deliveryPartnerService.registerProfileForSelf(user, 99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatePartnerTogglesActive() {
        City city = new City();
        city.setId(1L);
        DeliveryPartnerProfile profile = new DeliveryPartnerProfile();
        profile.setId(200L);
        profile.setCity(city);
        profile.setActive(false);
        when(profileRepository.findById(200L)).thenReturn(Optional.of(profile));

        var request = new DeliveryPartnerUpdateRequest(1L, true);
        DeliveryPartnerResponse response = deliveryPartnerService.updatePartner(200L, request);

        assertThat(response.active()).isTrue();
    }
}
```

- [ ] **Step 3: Run to confirm failure**

Run: `./mvnw test -Dtest=DeliveryPartnerServiceTest`
Expected: COMPILATION ERROR — package `delivery` doesn't exist yet.

- [ ] **Step 4: Create the entity and repository**

Create `src/main/java/com/fooddelivery/delivery/DeliveryPartnerProfile.java`:

```java
package com.fooddelivery.delivery;

import com.fooddelivery.city.City;
import com.fooddelivery.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "delivery_partner_profiles")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryPartnerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "city_id")
    private City city;

    @Column(nullable = false)
    private boolean active = false;
}
```

Create `src/main/java/com/fooddelivery/delivery/DeliveryPartnerProfileRepository.java`:

```java
package com.fooddelivery.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryPartnerProfileRepository extends JpaRepository<DeliveryPartnerProfile, Long> {
    Optional<DeliveryPartnerProfile> findByUserId(Long userId);
    List<DeliveryPartnerProfile> findByCityIdAndActiveTrue(Long cityId);
}
```

- [ ] **Step 5: Create the DTOs**

Create `src/main/java/com/fooddelivery/delivery/dto/AdminCreateDeliveryPartnerRequest.java`:

```java
package com.fooddelivery.delivery.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminCreateDeliveryPartnerRequest(
        @NotNull Long cityId,
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 100) String password
) {
}
```

Create `src/main/java/com/fooddelivery/delivery/dto/DeliveryPartnerUpdateRequest.java`:

```java
package com.fooddelivery.delivery.dto;

import jakarta.validation.constraints.NotNull;

public record DeliveryPartnerUpdateRequest(@NotNull Long cityId, boolean active) {
}
```

Create `src/main/java/com/fooddelivery/delivery/dto/DeliveryPartnerResponse.java`:

```java
package com.fooddelivery.delivery.dto;

public record DeliveryPartnerResponse(
        Long id, Long userId, String email, Long cityId, String cityName, boolean active
) {
}
```

- [ ] **Step 6: Create DeliveryPartnerService**

Create `src/main/java/com/fooddelivery/delivery/DeliveryPartnerService.java`:

```java
package com.fooddelivery.delivery;

import com.fooddelivery.city.City;
import com.fooddelivery.city.CityRepository;
import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.delivery.dto.AdminCreateDeliveryPartnerRequest;
import com.fooddelivery.delivery.dto.DeliveryPartnerResponse;
import com.fooddelivery.delivery.dto.DeliveryPartnerUpdateRequest;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.User;
import com.fooddelivery.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryPartnerService {

    private final DeliveryPartnerProfileRepository profileRepository;
    private final CityRepository cityRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public DeliveryPartnerResponse createPartner(AdminCreateDeliveryPartnerRequest request) {
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> new NotFoundException("City not found: " + request.cityId()));
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered: " + request.email());
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.DELIVERY_PARTNER);
        user = userRepository.save(user);

        DeliveryPartnerProfile profile = new DeliveryPartnerProfile();
        profile.setUser(user);
        profile.setCity(city);
        profile.setActive(true);
        return toResponse(profileRepository.save(profile));
    }

    @Transactional
    public DeliveryPartnerProfile registerProfileForSelf(User user, Long cityId) {
        City city = cityRepository.findById(cityId)
                .orElseThrow(() -> new NotFoundException("City not found: " + cityId));
        DeliveryPartnerProfile profile = new DeliveryPartnerProfile();
        profile.setUser(user);
        profile.setCity(city);
        profile.setActive(false);
        return profileRepository.save(profile);
    }

    public List<DeliveryPartnerResponse> listPartners() {
        return profileRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public DeliveryPartnerResponse updatePartner(Long profileId, DeliveryPartnerUpdateRequest request) {
        DeliveryPartnerProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new NotFoundException("Delivery partner profile not found: " + profileId));
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> new NotFoundException("City not found: " + request.cityId()));
        profile.setCity(city);
        profile.setActive(request.active());
        return toResponse(profile);
    }

    private DeliveryPartnerResponse toResponse(DeliveryPartnerProfile profile) {
        return new DeliveryPartnerResponse(profile.getId(), profile.getUser().getId(),
                profile.getUser().getEmail(), profile.getCity().getId(), profile.getCity().getName(),
                profile.isActive());
    }
}
```

- [ ] **Step 7: Run the unit test**

Run: `./mvnw test -Dtest=DeliveryPartnerServiceTest`
Expected: PASS (4 tests).

- [ ] **Step 8: Create the controller**

Create `src/main/java/com/fooddelivery/delivery/DeliveryPartnerController.java`:

```java
package com.fooddelivery.delivery;

import com.fooddelivery.delivery.dto.AdminCreateDeliveryPartnerRequest;
import com.fooddelivery.delivery.dto.DeliveryPartnerResponse;
import com.fooddelivery.delivery.dto.DeliveryPartnerUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/delivery-partners")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class DeliveryPartnerController {

    private final DeliveryPartnerService deliveryPartnerService;

    @PostMapping
    public ResponseEntity<DeliveryPartnerResponse> create(@Valid @RequestBody AdminCreateDeliveryPartnerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deliveryPartnerService.createPartner(request));
    }

    @GetMapping
    public List<DeliveryPartnerResponse> list() {
        return deliveryPartnerService.listPartners();
    }

    @PutMapping("/{id}")
    public DeliveryPartnerResponse update(@PathVariable Long id, @Valid @RequestBody DeliveryPartnerUpdateRequest request) {
        return deliveryPartnerService.updatePartner(id, request);
    }
}
```

- [ ] **Step 9: Extend AuthService to create a profile on delivery-partner self-registration**

Modify `src/main/java/com/fooddelivery/user/AuthService.java` — add one new
field, `DeliveryPartnerService deliveryPartnerService` (final, picked up by
the existing `@RequiredArgsConstructor`), add the imports
(`com.fooddelivery.common.exception.BadRequestException`,
`com.fooddelivery.delivery.DeliveryPartnerService`), and change the `register`
method body to:

```java
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.role() != Role.CUSTOMER && request.role() != Role.DELIVERY_PARTNER) {
            throw new ForbiddenException(
                    "Self-registration is only allowed for CUSTOMER or DELIVERY_PARTNER roles");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered: " + request.email());
        }
        if (request.role() == Role.DELIVERY_PARTNER && request.cityId() == null) {
            throw new BadRequestException("cityId is required when registering as a DELIVERY_PARTNER");
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user = userRepository.save(user);

        if (request.role() == Role.DELIVERY_PARTNER) {
            deliveryPartnerService.registerProfileForSelf(user, request.cityId());
        }

        return new AuthResponse(jwtService.generateToken(user), user.getId(), user.getRole());
    }
```

- [ ] **Step 10: Update AuthServiceTest for the new dependency**

Modify `src/test/java/com/fooddelivery/user/AuthServiceTest.java` — add
`@Mock private DeliveryPartnerService deliveryPartnerService;` as a field
(import `com.fooddelivery.delivery.DeliveryPartnerService`); no other change
is needed since the existing three tests never hit the `DELIVERY_PARTNER`
branch, and Mockito only fails on *used-but-unstubbed* mocks, not
unused ones.

- [ ] **Step 11: Run all unit tests**

Run: `./mvnw test -Dtest=AuthServiceTest,DeliveryPartnerServiceTest`
Expected: PASS.

- [ ] **Step 12: Write the integration test**

Create `src/test/java/com/fooddelivery/delivery/DeliveryPartnerControllerIT.java`:

```java
package com.fooddelivery.delivery;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.AbstractIntegrationTest;
import com.fooddelivery.delivery.dto.DeliveryPartnerResponse;
import com.fooddelivery.delivery.dto.DeliveryPartnerUpdateRequest;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.LoginRequest;
import com.fooddelivery.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryPartnerControllerIT extends AbstractIntegrationTest {

    private String adminToken() {
        var login = new LoginRequest("admin@fooddelivery.com", "Admin@123");
        return restTemplate.postForEntity("/auth/login", login, AuthResponse.class).getBody().token();
    }

    private <T> HttpEntity<T> authed(T body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void selfRegisteredPartnerStartsInactiveThenAdminActivates() {
        String admin = adminToken();
        Long cityId = restTemplate.exchange("/cities", HttpMethod.POST,
                authed(new CityRequest("Hyderabad", true), admin), CityResponse.class).getBody().id();

        var register = new RegisterRequest("partner-x@example.com", "password123", Role.DELIVERY_PARTNER, cityId);
        restTemplate.postForEntity("/auth/register", register, AuthResponse.class);

        ResponseEntity<DeliveryPartnerResponse[]> list = restTemplate.exchange(
                "/admin/delivery-partners", HttpMethod.GET, authed(null, admin), DeliveryPartnerResponse[].class);
        DeliveryPartnerResponse profile = list.getBody()[0];
        assertThat(profile.active()).isFalse();

        ResponseEntity<DeliveryPartnerResponse> updated = restTemplate.exchange(
                "/admin/delivery-partners/" + profile.id(), HttpMethod.PUT,
                authed(new DeliveryPartnerUpdateRequest(cityId, true), admin), DeliveryPartnerResponse.class);
        assertThat(updated.getBody().active()).isTrue();
    }

    @Test
    void registerDeliveryPartnerWithoutCityIdFails() {
        var register = new RegisterRequest("partner-y@example.com", "password123", Role.DELIVERY_PARTNER, null);

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", register, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
```

- [ ] **Step 13: Run all tests (requires Docker running)**

Run: `./mvnw test`
Expected: PASS.

- [ ] **Step 14: Commit**

```bash
git add src/main/java/com/fooddelivery/delivery src/main/java/com/fooddelivery/common/exception src/main/java/com/fooddelivery/user/AuthService.java src/test/java/com/fooddelivery/user/AuthServiceTest.java src/test/java/com/fooddelivery/delivery
git commit -m "Add delivery partner profiles: self-registration and admin management"
```

---

### Task 7: Order Placement — Stock Locking, Simulated Payment, Concurrency Test

**Files:**
- Create: `src/main/java/com/fooddelivery/order/OrderStatus.java`
- Create: `src/main/java/com/fooddelivery/order/Order.java`
- Create: `src/main/java/com/fooddelivery/order/OrderItem.java`
- Create: `src/main/java/com/fooddelivery/order/Payment.java`
- Create: `src/main/java/com/fooddelivery/order/OrderRepository.java`
- Create: `src/main/java/com/fooddelivery/order/OrderItemRepository.java`
- Create: `src/main/java/com/fooddelivery/order/PaymentRepository.java`
- Create: `src/main/java/com/fooddelivery/order/PaymentGateway.java`
- Create: `src/main/java/com/fooddelivery/order/SimulatedPaymentGateway.java`
- Create: `src/main/java/com/fooddelivery/common/exception/PaymentDeclinedException.java`
- Modify: `src/main/java/com/fooddelivery/common/exception/GlobalExceptionHandler.java`
- Modify: `src/main/java/com/fooddelivery/restaurant/MenuItemRepository.java`
- Create: `src/main/java/com/fooddelivery/order/dto/PlaceOrderRequest.java`
- Create: `src/main/java/com/fooddelivery/order/dto/OrderItemLine.java`
- Create: `src/main/java/com/fooddelivery/order/dto/OrderResponse.java`
- Create: `src/main/java/com/fooddelivery/order/OrderService.java`
- Create: `src/main/java/com/fooddelivery/order/OrderController.java`
- Test: `src/test/java/com/fooddelivery/order/OrderServiceTest.java`
- Test: `src/test/java/com/fooddelivery/order/OrderPlacementIT.java`
- Test: `src/test/java/com/fooddelivery/order/OrderPlacementConcurrencyIT.java`

**Interfaces:**
- Consumes: `Restaurant`/`RestaurantService.getRestaurantEntity` (Task 5); `MenuItem`/`MenuItemRepository` (Task 5, extended here); `User`/`Role` (Task 3); `NotFoundException`/`ConflictException`/`BadRequestException` (Tasks 2, 6).
- Produces: `OrderStatus` enum (`PLACED, ACCEPTED, PREPARING, OUT_FOR_DELIVERY, DELIVERED, REJECTED`) — Task 8's transition map and Task 9's assignment trigger both depend on it; `Order` (`Long id`, `User customer`, `Restaurant restaurant`, `OrderStatus status`, `BigDecimal totalAmount`, `Instant createdAt`, `Instant updatedAt`) and `OrderRepository` — Task 8 (`acceptOrder`/`rejectOrder`/`updateStatus`), Task 9 (`DeliveryAssignment.order`), Task 10 (notification recipients), Task 11 (`Rating.order`) all reference it directly; `OrderService.getOrderEntity(Long): Order` (throws `NotFoundException`) — the fetch-by-id pattern Task 8 builds its transition methods on top of; `PaymentGateway.charge(BigDecimal): void` (throws `PaymentDeclinedException`) bean, overridden with `@MockBean` in tests that need to force a decline.

- [ ] **Step 1: Add the pessimistic-lock query to MenuItemRepository**

Modify `src/main/java/com/fooddelivery/restaurant/MenuItemRepository.java` —
add the import (`jakarta.persistence.LockModeType`,
`org.springframework.data.jpa.repository.Lock`,
`org.springframework.data.jpa.repository.Query`,
`org.springframework.data.repository.query.Param`) and this method:

```java
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MenuItem m where m.id in :ids order by m.id")
    List<MenuItem> findAllByIdInForUpdate(@Param("ids") List<Long> ids);
```

- [ ] **Step 2: Create OrderStatus, exceptions, and the payment port**

Create `src/main/java/com/fooddelivery/order/OrderStatus.java`:

```java
package com.fooddelivery.order;

public enum OrderStatus {
    PLACED, ACCEPTED, PREPARING, OUT_FOR_DELIVERY, DELIVERED, REJECTED
}
```

Create `src/main/java/com/fooddelivery/common/exception/PaymentDeclinedException.java`:

```java
package com.fooddelivery.common.exception;

public class PaymentDeclinedException extends RuntimeException {
    public PaymentDeclinedException(String message) {
        super(message);
    }
}
```

Modify `src/main/java/com/fooddelivery/common/exception/GlobalExceptionHandler.java` —
add:

```java
    @ExceptionHandler(PaymentDeclinedException.class)
    public ProblemDetail handlePaymentDeclined(PaymentDeclinedException ex) {
        return problem(HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
    }
```

Create `src/main/java/com/fooddelivery/order/PaymentGateway.java`:

```java
package com.fooddelivery.order;

import java.math.BigDecimal;

public interface PaymentGateway {
    void charge(BigDecimal amount);
}
```

Create `src/main/java/com/fooddelivery/order/SimulatedPaymentGateway.java`:

```java
package com.fooddelivery.order;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * No real payment provider is in scope. This simulated gateway always
 * succeeds; tests that need to exercise the decline/rollback path override
 * this bean with a {@code @MockBean}.
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    @Override
    public void charge(BigDecimal amount) {
        // Always succeeds.
    }
}
```

- [ ] **Step 3: Create the Order, OrderItem, and Payment entities**

Create `src/main/java/com/fooddelivery/order/Order.java`:

```java
package com.fooddelivery.order;

import com.fooddelivery.restaurant.Restaurant;
import com.fooddelivery.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    private User customer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PLACED;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
```

Create `src/main/java/com/fooddelivery/order/OrderItem.java`:

```java
package com.fooddelivery.order;

import com.fooddelivery.restaurant.MenuItem;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(optional = false)
    @JoinColumn(name = "menu_item_id")
    private MenuItem menuItem;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_at_order", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPriceAtOrder;
}
```

Create `src/main/java/com/fooddelivery/order/Payment.java`:

```java
package com.fooddelivery.order;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
```

- [ ] **Step 4: Create the repositories**

Create `src/main/java/com/fooddelivery/order/OrderRepository.java`:

```java
package com.fooddelivery.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomerId(Long customerId);
    List<Order> findByRestaurantId(Long restaurantId);
}
```

Create `src/main/java/com/fooddelivery/order/OrderItemRepository.java`:

```java
package com.fooddelivery.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);
}
```

Create `src/main/java/com/fooddelivery/order/PaymentRepository.java`:

```java
package com.fooddelivery.order;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
```

- [ ] **Step 5: Create the DTOs**

Create `src/main/java/com/fooddelivery/order/dto/PlaceOrderRequest.java`:

```java
package com.fooddelivery.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PlaceOrderRequest(
        @NotNull Long restaurantId,
        @NotEmpty @Valid List<Item> items
) {
    public record Item(@NotNull Long menuItemId, @Min(1) int quantity) {
    }
}
```

Create `src/main/java/com/fooddelivery/order/dto/OrderItemLine.java`:

```java
package com.fooddelivery.order.dto;

import java.math.BigDecimal;

public record OrderItemLine(Long menuItemId, String menuItemName, int quantity, BigDecimal unitPrice) {
}
```

Create `src/main/java/com/fooddelivery/order/dto/OrderResponse.java`:

```java
package com.fooddelivery.order.dto;

import com.fooddelivery.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id, Long customerId, Long restaurantId, OrderStatus status,
        BigDecimal totalAmount, List<OrderItemLine> items,
        Instant createdAt, Instant updatedAt
) {
}
```

- [ ] **Step 6: Write the failing unit test for OrderService**

Create `src/test/java/com/fooddelivery/order/OrderServiceTest.java`:

```java
package com.fooddelivery.order;

import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.PaymentDeclinedException;
import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.order.dto.PlaceOrderRequest;
import com.fooddelivery.restaurant.MenuItem;
import com.fooddelivery.restaurant.MenuItemRepository;
import com.fooddelivery.restaurant.Restaurant;
import com.fooddelivery.restaurant.RestaurantService;
import com.fooddelivery.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private MenuItemRepository menuItemRepository;
    @Mock private RestaurantService restaurantService;
    @Mock private PaymentGateway paymentGateway;
    @InjectMocks private OrderService orderService;

    private MenuItem menuItem(Long id, Restaurant restaurant, BigDecimal price, int stock) {
        MenuItem item = new MenuItem();
        item.setId(id);
        item.setRestaurant(restaurant);
        item.setPrice(price);
        item.setStockQuantity(stock);
        item.setAvailable(true);
        return item;
    }

    @Test
    void placeOrderDecrementsStockAndChargesPayment() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(1L);
        when(restaurantService.getRestaurantEntity(1L)).thenReturn(restaurant);
        MenuItem item = menuItem(10L, restaurant, new BigDecimal("50.00"), 5);
        when(menuItemRepository.findAllByIdInForUpdate(List.of(10L))).thenReturn(List.of(item));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(100L);
            return o;
        });

        User customer = new User();
        customer.setId(1L);
        var request = new PlaceOrderRequest(1L, List.of(new PlaceOrderRequest.Item(10L, 2)));

        OrderResponse response = orderService.placeOrder(customer, request);

        assertThat(response.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(response.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(item.getStockQuantity()).isEqualTo(3);
        verify(paymentGateway).charge(new BigDecimal("100.00"));
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void placeOrderRejectsInsufficientStockWithoutCharging() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(1L);
        when(restaurantService.getRestaurantEntity(1L)).thenReturn(restaurant);
        MenuItem item = menuItem(10L, restaurant, new BigDecimal("50.00"), 1);
        when(menuItemRepository.findAllByIdInForUpdate(List.of(10L))).thenReturn(List.of(item));

        User customer = new User();
        customer.setId(1L);
        var request = new PlaceOrderRequest(1L, List.of(new PlaceOrderRequest.Item(10L, 2)));

        assertThatThrownBy(() -> orderService.placeOrder(customer, request))
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(paymentGateway);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrderPropagatesPaymentDecline() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(1L);
        when(restaurantService.getRestaurantEntity(1L)).thenReturn(restaurant);
        MenuItem item = menuItem(10L, restaurant, new BigDecimal("50.00"), 5);
        when(menuItemRepository.findAllByIdInForUpdate(List.of(10L))).thenReturn(List.of(item));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new PaymentDeclinedException("card declined")).when(paymentGateway).charge(any());

        User customer = new User();
        customer.setId(1L);
        var request = new PlaceOrderRequest(1L, List.of(new PlaceOrderRequest.Item(10L, 1)));

        assertThatThrownBy(() -> orderService.placeOrder(customer, request))
                .isInstanceOf(PaymentDeclinedException.class);

        verify(paymentRepository, never()).save(any());
    }
}
```

- [ ] **Step 7: Run to confirm failure**

Run: `./mvnw test -Dtest=OrderServiceTest`
Expected: COMPILATION ERROR — `OrderService` etc. don't exist.

- [ ] **Step 8: Create OrderService**

Create `src/main/java/com/fooddelivery/order/OrderService.java`:

```java
package com.fooddelivery.order;

import com.fooddelivery.common.exception.BadRequestException;
import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.order.dto.OrderItemLine;
import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.order.dto.PlaceOrderRequest;
import com.fooddelivery.restaurant.MenuItem;
import com.fooddelivery.restaurant.MenuItemRepository;
import com.fooddelivery.restaurant.Restaurant;
import com.fooddelivery.restaurant.RestaurantService;
import com.fooddelivery.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final MenuItemRepository menuItemRepository;
    private final RestaurantService restaurantService;
    private final PaymentGateway paymentGateway;

    @Transactional
    public OrderResponse placeOrder(User customer, PlaceOrderRequest request) {
        Restaurant restaurant = restaurantService.getRestaurantEntity(request.restaurantId());

        List<Long> ids = request.items().stream().map(PlaceOrderRequest.Item::menuItemId).sorted().toList();
        List<MenuItem> lockedItems = menuItemRepository.findAllByIdInForUpdate(ids);
        Map<Long, MenuItem> itemsById = new HashMap<>();
        for (MenuItem item : lockedItems) {
            itemsById.put(item.getId(), item);
        }

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> orderItems = new java.util.ArrayList<>();
        for (PlaceOrderRequest.Item requested : request.items()) {
            MenuItem item = itemsById.get(requested.menuItemId());
            if (item == null) {
                throw new NotFoundException("Menu item not found: " + requested.menuItemId());
            }
            if (!item.getRestaurant().getId().equals(restaurant.getId())) {
                throw new BadRequestException("Menu item " + item.getId() + " does not belong to restaurant " + restaurant.getId());
            }
            if (!item.isAvailable() || item.getStockQuantity() < requested.quantity()) {
                throw new ConflictException("Insufficient stock for menu item: " + item.getId());
            }
            item.setStockQuantity(item.getStockQuantity() - requested.quantity());

            OrderItem orderItem = new OrderItem();
            orderItem.setMenuItem(item);
            orderItem.setQuantity(requested.quantity());
            orderItem.setUnitPriceAtOrder(item.getPrice());
            orderItems.add(orderItem);

            total = total.add(item.getPrice().multiply(BigDecimal.valueOf(requested.quantity())));
        }

        Order order = new Order();
        order.setCustomer(customer);
        order.setRestaurant(restaurant);
        order.setStatus(OrderStatus.PLACED);
        order.setTotalAmount(total);
        order = orderRepository.save(order);

        for (OrderItem orderItem : orderItems) {
            orderItem.setOrder(order);
        }
        orderItemRepository.saveAll(orderItems);

        paymentGateway.charge(total);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(total);
        paymentRepository.save(payment);

        return toResponse(order, orderItems);
    }

    public Order getOrderEntity(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found: " + id));
    }

    public OrderResponse getOrder(Long id) {
        Order order = getOrderEntity(id);
        return toResponse(order, orderItemRepository.findByOrderId(id));
    }

    public List<OrderResponse> listCustomerOrders(Long customerId) {
        return orderRepository.findByCustomerId(customerId).stream()
                .map(order -> toResponse(order, orderItemRepository.findByOrderId(order.getId())))
                .toList();
    }

    private OrderResponse toResponse(Order order, List<OrderItem> items) {
        List<OrderItemLine> lines = items.stream()
                .map(oi -> new OrderItemLine(oi.getMenuItem().getId(), oi.getMenuItem().getName(),
                        oi.getQuantity(), oi.getUnitPriceAtOrder()))
                .toList();
        return new OrderResponse(order.getId(), order.getCustomer().getId(), order.getRestaurant().getId(),
                order.getStatus(), order.getTotalAmount(), lines, order.getCreatedAt(), order.getUpdatedAt());
    }
}
```

- [ ] **Step 9: Run the unit test**

Run: `./mvnw test -Dtest=OrderServiceTest`
Expected: PASS (3 tests).

- [ ] **Step 10: Create OrderController**

Create `src/main/java/com/fooddelivery/order/OrderController.java`:

```java
package com.fooddelivery.order;

import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.order.dto.PlaceOrderRequest;
import com.fooddelivery.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> place(@AuthenticationPrincipal User customer,
                                                @Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(customer, request));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.getOrder(id);
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<OrderResponse> myOrders(@AuthenticationPrincipal User customer) {
        return orderService.listCustomerOrders(customer.getId());
    }
}
```

- [ ] **Step 11: Write the order-placement integration tests**

Create `src/test/java/com/fooddelivery/order/OrderPlacementIT.java`:

```java
package com.fooddelivery.order;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.AbstractIntegrationTest;
import com.fooddelivery.common.exception.PaymentDeclinedException;
import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.order.dto.PlaceOrderRequest;
import com.fooddelivery.restaurant.dto.AdminCreateRestaurantRequest;
import com.fooddelivery.restaurant.dto.MenuItemRequest;
import com.fooddelivery.restaurant.dto.MenuItemResponse;
import com.fooddelivery.restaurant.dto.RestaurantResponse;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.LoginRequest;
import com.fooddelivery.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

class OrderPlacementIT extends AbstractIntegrationTest {

    @MockBean
    private PaymentGateway paymentGateway;

    private String adminToken() {
        var login = new LoginRequest("admin@fooddelivery.com", "Admin@123");
        return restTemplate.postForEntity("/auth/login", login, AuthResponse.class).getBody().token();
    }

    private <T> HttpEntity<T> authed(T body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private record Setup(Long restaurantId, Long menuItemId, String customerToken) {
    }

    private Setup setUpRestaurantWithStock(int stock, String suffix) {
        String admin = adminToken();
        Long cityId = restTemplate.exchange("/cities", HttpMethod.POST,
                authed(new CityRequest("City" + suffix, true), admin), CityResponse.class).getBody().id();
        var createReq = new AdminCreateRestaurantRequest(cityId, "Rest" + suffix, "Addr",
                "owner" + suffix + "@example.com", "password123");
        Long restaurantId = restTemplate.exchange("/admin/restaurants", HttpMethod.POST,
                authed(createReq, admin), RestaurantResponse.class).getBody().id();
        String ownerToken = restTemplate.postForEntity("/auth/login",
                new LoginRequest("owner" + suffix + "@example.com", "password123"), AuthResponse.class).getBody().token();
        var menuReq = new MenuItemRequest("Item" + suffix, new BigDecimal("25.00"), stock, true);
        Long menuItemId = restTemplate.exchange("/restaurants/" + restaurantId + "/menu-items", HttpMethod.POST,
                authed(menuReq, ownerToken), MenuItemResponse.class).getBody().id();
        var register = new RegisterRequest("cust" + suffix + "@example.com", "password123", Role.CUSTOMER, null);
        String customerToken = restTemplate.postForEntity("/auth/register", register, AuthResponse.class).getBody().token();
        return new Setup(restaurantId, menuItemId, customerToken);
    }

    @Test
    void placingOrderDecrementsStockAndCreatesPayment() {
        Setup setup = setUpRestaurantWithStock(10, "A");
        var request = new PlaceOrderRequest(setup.restaurantId(), java.util.List.of(new PlaceOrderRequest.Item(setup.menuItemId(), 3)));

        ResponseEntity<OrderResponse> response = restTemplate.exchange("/orders", HttpMethod.POST,
                authed(request, setup.customerToken()), OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.PLACED);
        assertThat(response.getBody().totalAmount()).isEqualByComparingTo("75.00");
    }

    @Test
    void placingOrderExceedingStockReturns409() {
        Setup setup = setUpRestaurantWithStock(2, "B");
        var request = new PlaceOrderRequest(setup.restaurantId(), java.util.List.of(new PlaceOrderRequest.Item(setup.menuItemId(), 5)));

        ResponseEntity<String> response = restTemplate.exchange("/orders", HttpMethod.POST,
                authed(request, setup.customerToken()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void declinedPaymentRollsBackStockDecrement() {
        Setup setup = setUpRestaurantWithStock(10, "C");
        doThrow(new PaymentDeclinedException("simulated decline")).when(paymentGateway).charge(any());
        var request = new PlaceOrderRequest(setup.restaurantId(), java.util.List.of(new PlaceOrderRequest.Item(setup.menuItemId(), 4)));

        ResponseEntity<String> response = restTemplate.exchange("/orders", HttpMethod.POST,
                authed(request, setup.customerToken()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);

        ResponseEntity<MenuItemResponse[]> menu = restTemplate.getForEntity(
                "/restaurants/" + setup.restaurantId() + "/menu", MenuItemResponse[].class);
        assertThat(menu.getBody()[0].stockQuantity()).isEqualTo(10);
    }
}
```

- [ ] **Step 12: Write the concurrency test — the core requirement**

Create `src/test/java/com/fooddelivery/order/OrderPlacementConcurrencyIT.java`:

```java
package com.fooddelivery.order;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.AbstractIntegrationTest;
import com.fooddelivery.order.dto.PlaceOrderRequest;
import com.fooddelivery.restaurant.dto.AdminCreateRestaurantRequest;
import com.fooddelivery.restaurant.dto.MenuItemRequest;
import com.fooddelivery.restaurant.dto.MenuItemResponse;
import com.fooddelivery.restaurant.dto.RestaurantResponse;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.LoginRequest;
import com.fooddelivery.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPlacementConcurrencyIT extends AbstractIntegrationTest {

    private <T> HttpEntity<T> authed(T body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void concurrentOrdersNeverOversellLimitedStock() throws InterruptedException {
        String admin = restTemplate.postForEntity("/auth/login",
                new LoginRequest("admin@fooddelivery.com", "Admin@123"), AuthResponse.class).getBody().token();
        Long cityId = restTemplate.exchange("/cities", HttpMethod.POST,
                authed(new CityRequest("ConcurrencyCity", true), admin), CityResponse.class).getBody().id();
        var createReq = new AdminCreateRestaurantRequest(cityId, "ConcurrencyRest", "Addr",
                "concurrency-owner@example.com", "password123");
        Long restaurantId = restTemplate.exchange("/admin/restaurants", HttpMethod.POST,
                authed(createReq, admin), RestaurantResponse.class).getBody().id();
        String ownerToken = restTemplate.postForEntity("/auth/login",
                new LoginRequest("concurrency-owner@example.com", "password123"), AuthResponse.class).getBody().token();

        int stock = 5;
        int concurrentOrders = 15;
        Long menuItemId = restTemplate.exchange("/restaurants/" + restaurantId + "/menu-items", HttpMethod.POST,
                authed(new MenuItemRequest("LimitedItem", new BigDecimal("10.00"), stock, true), ownerToken),
                MenuItemResponse.class).getBody().id();

        List<String> customerTokens = new ArrayList<>();
        for (int i = 0; i < concurrentOrders; i++) {
            var register = new RegisterRequest("racer" + i + "@example.com", "password123", Role.CUSTOMER, null);
            customerTokens.add(restTemplate.postForEntity("/auth/register", register, AuthResponse.class).getBody().token());
        }

        ExecutorService pool = Executors.newFixedThreadPool(concurrentOrders);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (String token : customerTokens) {
            futures.add(pool.submit(() -> {
                try {
                    startLatch.await();
                    var request = new PlaceOrderRequest(restaurantId, List.of(new PlaceOrderRequest.Item(menuItemId, 1)));
                    ResponseEntity<String> response = restTemplate.exchange(
                            "/orders", HttpMethod.POST, authed(request, token), String.class);
                    if (response.getStatusCode() == HttpStatus.CREATED) {
                        successCount.incrementAndGet();
                    } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        startLatch.countDown();
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
        pool.shutdown();

        assertThat(successCount.get()).isEqualTo(stock);
        assertThat(conflictCount.get()).isEqualTo(concurrentOrders - stock);

        ResponseEntity<MenuItemResponse[]> menu = restTemplate.getForEntity(
                "/restaurants/" + restaurantId + "/menu", MenuItemResponse[].class);
        assertThat(menu.getBody()[0].stockQuantity()).isEqualTo(0);
    }
}
```

- [ ] **Step 13: Run all tests (requires Docker running)**

Run: `./mvnw test`
Expected: PASS — the concurrency test is the direct proof of the "no
oversell" requirement.

- [ ] **Step 14: Commit**

```bash
git add src/main/java/com/fooddelivery/order src/main/java/com/fooddelivery/common/exception src/main/java/com/fooddelivery/restaurant/MenuItemRepository.java src/test/java/com/fooddelivery/order docs/superpowers/specs/2026-09-23-food-delivery-order-management-design.md docs/superpowers/plans/2026-09-23-food-delivery-order-management.md
git commit -m "Add order placement with pessimistic stock locking and simulated payment"
```

---

### Task 8: Order Lifecycle Transitions (Restaurant Owner)

**Files:**
- Modify: `src/main/java/com/fooddelivery/order/OrderService.java`
- Modify: `src/main/java/com/fooddelivery/order/OrderController.java`
- Modify: `src/main/java/com/fooddelivery/restaurant/RestaurantController.java`
- Create: `src/main/java/com/fooddelivery/order/dto/UpdateStatusRequest.java`
- Test: `src/test/java/com/fooddelivery/order/OrderLifecycleServiceTest.java`
- Test: `src/test/java/com/fooddelivery/order/OrderLifecycleControllerIT.java`

**Interfaces:**
- Consumes: `Order`/`OrderStatus`/`OrderService.getOrderEntity` (Task 7); `RestaurantService.getOwnedRestaurantEntity` (Task 5); `ForbiddenException`/`ConflictException` (Task 2).
- Produces: `OrderService.acceptOrder(Long ownerId, Long orderId): OrderResponse`, `.rejectOrder(Long ownerId, Long orderId): OrderResponse`, `.updateStatus(User caller, Long orderId, OrderStatus target): OrderResponse`, `.listRestaurantOrders(Long ownerId, Long restaurantId): List<OrderResponse>` — Task 9 modifies `updateStatus` to add the delivery-partner branch (currently the `else` branch unconditionally throws `ForbiddenException`) and modifies `acceptOrder` to also create a `DeliveryAssignment`.

- [ ] **Step 1: Write the failing unit test**

Create `src/test/java/com/fooddelivery/order/OrderLifecycleServiceTest.java`:

```java
package com.fooddelivery.order;

import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.ForbiddenException;
import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.restaurant.MenuItem;
import com.fooddelivery.restaurant.Restaurant;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderLifecycleServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private com.fooddelivery.restaurant.MenuItemRepository menuItemRepository;
    @Mock private com.fooddelivery.restaurant.RestaurantService restaurantService;
    @Mock private PaymentGateway paymentGateway;
    @InjectMocks private OrderService orderService;

    private Order orderWithOwner(Long ownerId, OrderStatus status) {
        User owner = new User();
        owner.setId(ownerId);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setOwner(owner);
        Order order = new Order();
        order.setId(5L);
        order.setRestaurant(restaurant);
        order.setStatus(status);
        return order;
    }

    @Test
    void acceptOrderTransitionsPlacedToAccepted() {
        Order order = orderWithOwner(1L, OrderStatus.PLACED);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(5L)).thenReturn(List.of());

        OrderResponse response = orderService.acceptOrder(1L, 5L);

        assertThat(response.status()).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    void acceptOrderRejectsNonOwner() {
        Order order = orderWithOwner(1L, OrderStatus.PLACED);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.acceptOrder(2L, 5L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void acceptOrderRejectsIllegalTransition() {
        Order order = orderWithOwner(1L, OrderStatus.DELIVERED);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.acceptOrder(1L, 5L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectOrderRestoresStock() {
        Order order = orderWithOwner(1L, OrderStatus.PLACED);
        MenuItem menuItem = new MenuItem();
        menuItem.setId(10L);
        menuItem.setStockQuantity(2);
        OrderItem orderItem = new OrderItem();
        orderItem.setMenuItem(menuItem);
        orderItem.setQuantity(3);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(5L)).thenReturn(List.of(orderItem));

        OrderResponse response = orderService.rejectOrder(1L, 5L);

        assertThat(response.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(menuItem.getStockQuantity()).isEqualTo(5);
    }

    @Test
    void updateStatusRejectsNonOwnerCallerForPreparing() {
        Order order = orderWithOwner(1L, OrderStatus.ACCEPTED);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        User deliveryPartner = new User();
        deliveryPartner.setId(99L);
        deliveryPartner.setRole(Role.DELIVERY_PARTNER);

        assertThatThrownBy(() -> orderService.updateStatus(deliveryPartner, 5L, OrderStatus.PREPARING))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateStatusOwnerCanMarkPreparing() {
        Order order = orderWithOwner(1L, OrderStatus.ACCEPTED);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(5L)).thenReturn(List.of());
        User owner = new User();
        owner.setId(1L);
        owner.setRole(Role.RESTAURANT_OWNER);

        OrderResponse response = orderService.updateStatus(owner, 5L, OrderStatus.PREPARING);

        assertThat(response.status()).isEqualTo(OrderStatus.PREPARING);
    }
}
```

- [ ] **Step 2: Run to confirm failure**

Run: `./mvnw test -Dtest=OrderLifecycleServiceTest`
Expected: COMPILATION ERROR — `acceptOrder`/`rejectOrder`/`updateStatus` don't exist on `OrderService` yet.

- [ ] **Step 3: Add the transition map and lifecycle methods to OrderService**

Modify `src/main/java/com/fooddelivery/order/OrderService.java` — add these
imports (`com.fooddelivery.common.exception.ForbiddenException`,
`com.fooddelivery.user.Role`, `com.fooddelivery.restaurant.MenuItem`,
`java.util.Map`, `java.util.Set`) and these members:

```java
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            OrderStatus.PLACED, Set.of(OrderStatus.ACCEPTED, OrderStatus.REJECTED),
            OrderStatus.ACCEPTED, Set.of(OrderStatus.PREPARING),
            OrderStatus.PREPARING, Set.of(OrderStatus.OUT_FOR_DELIVERY),
            OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, Set.of(),
            OrderStatus.REJECTED, Set.of()
    );

    private void transition(Order order, OrderStatus target) {
        Set<OrderStatus> allowed = TRANSITIONS.getOrDefault(order.getStatus(), Set.of());
        if (!allowed.contains(target)) {
            throw new ConflictException(
                    "Cannot transition order " + order.getId() + " from " + order.getStatus() + " to " + target);
        }
        order.setStatus(target);
        order.setUpdatedAt(java.time.Instant.now());
    }

    private void verifyRestaurantOwnership(Order order, Long ownerId) {
        if (!order.getRestaurant().getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("You do not own the restaurant for order " + order.getId());
        }
    }

    @Transactional
    public OrderResponse acceptOrder(Long ownerId, Long orderId) {
        Order order = getOrderEntity(orderId);
        verifyRestaurantOwnership(order, ownerId);
        transition(order, OrderStatus.ACCEPTED);
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }

    @Transactional
    public OrderResponse rejectOrder(Long ownerId, Long orderId) {
        Order order = getOrderEntity(orderId);
        verifyRestaurantOwnership(order, ownerId);
        transition(order, OrderStatus.REJECTED);
        for (OrderItem orderItem : orderItemRepository.findByOrderId(orderId)) {
            MenuItem item = orderItem.getMenuItem();
            item.setStockQuantity(item.getStockQuantity() + orderItem.getQuantity());
        }
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }

    @Transactional
    public OrderResponse updateStatus(User caller, Long orderId, OrderStatus target) {
        Order order = getOrderEntity(orderId);
        if (caller.getRole() == Role.RESTAURANT_OWNER) {
            verifyRestaurantOwnership(order, caller.getId());
            if (target != OrderStatus.PREPARING) {
                throw new ForbiddenException("Restaurant owners may only mark orders PREPARING via this endpoint");
            }
        } else {
            throw new ForbiddenException("You are not authorized to update this order's status");
        }
        transition(order, target);
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }

    public List<OrderResponse> listRestaurantOrders(Long ownerId, Long restaurantId) {
        restaurantService.getOwnedRestaurantEntity(restaurantId, ownerId);
        return orderRepository.findByRestaurantId(restaurantId).stream()
                .map(order -> toResponse(order, orderItemRepository.findByOrderId(order.getId())))
                .toList();
    }
```

(The `else` branch in `updateStatus` unconditionally forbids everyone but
the owner — Task 9 replaces it with an `else if (caller.getRole() ==
Role.DELIVERY_PARTNER)` branch that checks assignment.)

- [ ] **Step 4: Run the unit test**

Run: `./mvnw test -Dtest=OrderLifecycleServiceTest`
Expected: PASS (6 tests).

- [ ] **Step 5: Create UpdateStatusRequest and extend OrderController**

Create `src/main/java/com/fooddelivery/order/dto/UpdateStatusRequest.java`:

```java
package com.fooddelivery.order.dto;

import com.fooddelivery.order.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull OrderStatus status) {
}
```

Modify `src/main/java/com/fooddelivery/order/OrderController.java` — add:

```java
    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public OrderResponse accept(@PathVariable Long id, @AuthenticationPrincipal User owner) {
        return orderService.acceptOrder(owner.getId(), id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public OrderResponse reject(@PathVariable Long id, @AuthenticationPrincipal User owner) {
        return orderService.rejectOrder(owner.getId(), id);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'DELIVERY_PARTNER')")
    public OrderResponse updateStatus(@PathVariable Long id, @AuthenticationPrincipal User caller,
                                       @jakarta.validation.Valid @RequestBody com.fooddelivery.order.dto.UpdateStatusRequest request) {
        return orderService.updateStatus(caller, id, request.status());
    }
```

(add the corresponding `UpdateStatusRequest` import at the top instead of
using the fully-qualified name, if preferred)

- [ ] **Step 6: Add the restaurant-scoped order listing endpoint**

Modify `src/main/java/com/fooddelivery/restaurant/RestaurantController.java` —
add a final `OrderService orderService` field (picked up by the existing
`@RequiredArgsConstructor`) and this method (import
`com.fooddelivery.order.OrderService`, `com.fooddelivery.order.dto.OrderResponse`,
`com.fooddelivery.user.User`,
`org.springframework.security.core.annotation.AuthenticationPrincipal`):

```java
    @GetMapping("/restaurants/{id}/orders")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public List<OrderResponse> restaurantOrders(@PathVariable Long id, @AuthenticationPrincipal User owner) {
        return orderService.listRestaurantOrders(owner.getId(), id);
    }
```

- [ ] **Step 7: Write the integration test**

Create `src/test/java/com/fooddelivery/order/OrderLifecycleControllerIT.java`:

```java
package com.fooddelivery.order;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.AbstractIntegrationTest;
import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.order.dto.PlaceOrderRequest;
import com.fooddelivery.order.dto.UpdateStatusRequest;
import com.fooddelivery.restaurant.dto.AdminCreateRestaurantRequest;
import com.fooddelivery.restaurant.dto.MenuItemRequest;
import com.fooddelivery.restaurant.dto.MenuItemResponse;
import com.fooddelivery.restaurant.dto.RestaurantResponse;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.LoginRequest;
import com.fooddelivery.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderLifecycleControllerIT extends AbstractIntegrationTest {

    private <T> HttpEntity<T> authed(T body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void ownerAcceptsThenMarksPreparingThenRejectionRestoresStockOnASeparateOrder() {
        String admin = restTemplate.postForEntity("/auth/login",
                new LoginRequest("admin@fooddelivery.com", "Admin@123"), AuthResponse.class).getBody().token();
        Long cityId = restTemplate.exchange("/cities", HttpMethod.POST,
                authed(new CityRequest("LifecycleCity", true), admin), CityResponse.class).getBody().id();
        var createReq = new AdminCreateRestaurantRequest(cityId, "LifecycleRest", "Addr",
                "lifecycle-owner@example.com", "password123");
        Long restaurantId = restTemplate.exchange("/admin/restaurants", HttpMethod.POST,
                authed(createReq, admin), RestaurantResponse.class).getBody().id();
        String ownerToken = restTemplate.postForEntity("/auth/login",
                new LoginRequest("lifecycle-owner@example.com", "password123"), AuthResponse.class).getBody().token();
        Long menuItemId = restTemplate.exchange("/restaurants/" + restaurantId + "/menu-items", HttpMethod.POST,
                authed(new MenuItemRequest("Item", new BigDecimal("30.00"), 10, true), ownerToken),
                MenuItemResponse.class).getBody().id();
        String customerToken = restTemplate.postForEntity("/auth/register",
                new RegisterRequest("lifecycle-cust@example.com", "password123", Role.CUSTOMER, null),
                AuthResponse.class).getBody().token();

        var request = new PlaceOrderRequest(restaurantId, List.of(new PlaceOrderRequest.Item(menuItemId, 2)));
        Long orderId = restTemplate.exchange("/orders", HttpMethod.POST,
                authed(request, customerToken), OrderResponse.class).getBody().id();

        ResponseEntity<OrderResponse> accepted = restTemplate.exchange(
                "/orders/" + orderId + "/accept", HttpMethod.POST, authed(null, ownerToken), OrderResponse.class);
        assertThat(accepted.getBody().status()).isEqualTo(OrderStatus.ACCEPTED);

        ResponseEntity<OrderResponse> preparing = restTemplate.exchange(
                "/orders/" + orderId + "/status", HttpMethod.POST,
                authed(new UpdateStatusRequest(OrderStatus.PREPARING), ownerToken), OrderResponse.class);
        assertThat(preparing.getBody().status()).isEqualTo(OrderStatus.PREPARING);

        ResponseEntity<OrderResponse[]> restaurantOrders = restTemplate.exchange(
                "/restaurants/" + restaurantId + "/orders", HttpMethod.GET, authed(null, ownerToken), OrderResponse[].class);
        assertThat(restaurantOrders.getBody()).hasSize(1);

        var secondOrderReq = new PlaceOrderRequest(restaurantId, List.of(new PlaceOrderRequest.Item(menuItemId, 3)));
        Long secondOrderId = restTemplate.exchange("/orders", HttpMethod.POST,
                authed(secondOrderReq, customerToken), OrderResponse.class).getBody().id();

        ResponseEntity<OrderResponse> rejected = restTemplate.exchange(
                "/orders/" + secondOrderId + "/reject", HttpMethod.POST, authed(null, ownerToken), OrderResponse.class);
        assertThat(rejected.getBody().status()).isEqualTo(OrderStatus.REJECTED);

        ResponseEntity<MenuItemResponse[]> menu = restTemplate.getForEntity(
                "/restaurants/" + restaurantId + "/menu", MenuItemResponse[].class);
        // stock was 10, -2 (first order) -3 (second order, then restored on reject) = 8
        assertThat(menu.getBody()[0].stockQuantity()).isEqualTo(8);
    }
}
```

- [ ] **Step 8: Run all tests (requires Docker running)**

Run: `./mvnw test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/fooddelivery/order src/main/java/com/fooddelivery/restaurant/RestaurantController.java src/test/java/com/fooddelivery/order
git commit -m "Add restaurant-owner order lifecycle transitions"
```

---

### Task 9: Delivery Partner Assignment — Contention Handling

**Files:**
- Create: `src/main/java/com/fooddelivery/delivery/DeliveryAssignmentStatus.java`
- Create: `src/main/java/com/fooddelivery/delivery/DeliveryAssignment.java`
- Create: `src/main/java/com/fooddelivery/delivery/DeliveryAssignmentRepository.java`
- Create: `src/main/java/com/fooddelivery/delivery/dto/AssignmentResponse.java`
- Create: `src/main/java/com/fooddelivery/delivery/DeliveryAssignmentService.java`
- Create: `src/main/java/com/fooddelivery/delivery/DeliveryAssignmentController.java`
- Modify: `src/main/java/com/fooddelivery/order/OrderService.java`
- Test: `src/test/java/com/fooddelivery/delivery/DeliveryAssignmentServiceTest.java`
- Test: `src/test/java/com/fooddelivery/delivery/DeliveryAssignmentControllerIT.java`
- Test: `src/test/java/com/fooddelivery/delivery/DeliveryAssignmentConcurrencyIT.java`

**Interfaces:**
- Consumes: `Order`/`OrderStatus`/`OrderService.acceptOrder`/`.updateStatus` (Tasks 7-8); `DeliveryPartnerProfile`/`DeliveryPartnerProfileRepository` (Task 6); `NotFoundException`/`ConflictException`/`ForbiddenException` (Task 2).
- Produces: `DeliveryAssignmentStatus` (`OPEN, ACCEPTED`); `DeliveryAssignment` (`Long id`, `Order order`, `DeliveryAssignmentStatus status`, `User partner` nullable, `Instant offeredAt`, `Instant acceptedAt` nullable); `DeliveryAssignmentService.createOpenAssignment(Order): DeliveryAssignment`, `.isAssignedPartner(Long orderId, Long partnerId): boolean` — both consumed by `OrderService` in this same task; Task 10 subscribes to the same `AssignmentAcceptedEvent` this task's `acceptAssignment` publishes.

- [ ] **Step 1: Create the status enum, entity, and repository**

Create `src/main/java/com/fooddelivery/delivery/DeliveryAssignmentStatus.java`:

```java
package com.fooddelivery.delivery;

public enum DeliveryAssignmentStatus {
    OPEN, ACCEPTED
}
```

Create `src/main/java/com/fooddelivery/delivery/DeliveryAssignment.java`:

```java
package com.fooddelivery.delivery;

import com.fooddelivery.order.Order;
import com.fooddelivery.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "delivery_assignments")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryAssignmentStatus status = DeliveryAssignmentStatus.OPEN;

    @ManyToOne
    @JoinColumn(name = "partner_id")
    private User partner;

    @Column(name = "offered_at", nullable = false)
    private Instant offeredAt = Instant.now();

    @Column(name = "accepted_at")
    private Instant acceptedAt;
}
```

Create `src/main/java/com/fooddelivery/delivery/DeliveryAssignmentRepository.java`:

```java
package com.fooddelivery.delivery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, Long> {

    Optional<DeliveryAssignment> findByOrderId(Long orderId);

    @Query("select da from DeliveryAssignment da where da.status = com.fooddelivery.delivery.DeliveryAssignmentStatus.OPEN and da.order.restaurant.city.id = :cityId")
    List<DeliveryAssignment> findOpenByCityId(@Param("cityId") Long cityId);

    @Modifying
    @Query(value = "UPDATE delivery_assignments SET status = 'ACCEPTED', partner_id = :partnerId, accepted_at = now() WHERE id = :id AND status = 'OPEN'", nativeQuery = true)
    int acceptIfOpen(@Param("id") Long id, @Param("partnerId") Long partnerId);
}
```

- [ ] **Step 2: Create the DTO**

Create `src/main/java/com/fooddelivery/delivery/dto/AssignmentResponse.java`:

```java
package com.fooddelivery.delivery.dto;

import com.fooddelivery.delivery.DeliveryAssignmentStatus;

import java.time.Instant;

public record AssignmentResponse(
        Long id, Long orderId, DeliveryAssignmentStatus status, Long partnerId,
        Instant offeredAt, Instant acceptedAt
) {
}
```

- [ ] **Step 3: Write the failing unit test**

Create `src/test/java/com/fooddelivery/delivery/DeliveryAssignmentServiceTest.java`:

```java
package com.fooddelivery.delivery;

import com.fooddelivery.city.City;
import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.ForbiddenException;
import com.fooddelivery.delivery.dto.AssignmentResponse;
import com.fooddelivery.order.Order;
import com.fooddelivery.restaurant.Restaurant;
import com.fooddelivery.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryAssignmentServiceTest {

    @Mock private DeliveryAssignmentRepository assignmentRepository;
    @Mock private DeliveryPartnerProfileRepository profileRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private DeliveryAssignmentService deliveryAssignmentService;

    private City city(Long id) {
        City c = new City();
        c.setId(id);
        return c;
    }

    private Order orderInCity(Long cityId) {
        Restaurant restaurant = new Restaurant();
        restaurant.setCity(city(cityId));
        Order order = new Order();
        order.setId(1L);
        order.setRestaurant(restaurant);
        return order;
    }

    private DeliveryAssignment openAssignment(Long cityId) {
        DeliveryAssignment assignment = new DeliveryAssignment();
        assignment.setId(50L);
        assignment.setOrder(orderInCity(cityId));
        assignment.setStatus(DeliveryAssignmentStatus.OPEN);
        return assignment;
    }

    private DeliveryPartnerProfile activeProfile(Long userId, Long cityId) {
        User user = new User();
        user.setId(userId);
        DeliveryPartnerProfile profile = new DeliveryPartnerProfile();
        profile.setUser(user);
        profile.setCity(city(cityId));
        profile.setActive(true);
        return profile;
    }

    @Test
    void acceptAssignmentSucceedsForEligiblePartner() {
        DeliveryAssignment assignment = openAssignment(1L);
        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(assignment));
        User partnerUser = new User();
        partnerUser.setId(7L);
        when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(activeProfile(7L, 1L)));
        when(assignmentRepository.acceptIfOpen(50L, 7L)).thenReturn(1);
        DeliveryAssignment accepted = openAssignment(1L);
        accepted.setStatus(DeliveryAssignmentStatus.ACCEPTED);
        accepted.setPartner(partnerUser);
        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(assignment), Optional.of(accepted));

        AssignmentResponse response = deliveryAssignmentService.acceptAssignment(partnerUser, 50L);

        assertThat(response.status()).isEqualTo(DeliveryAssignmentStatus.ACCEPTED);
        assertThat(response.partnerId()).isEqualTo(7L);
    }

    @Test
    void acceptAssignmentFailsWhenAlreadyTaken() {
        DeliveryAssignment assignment = openAssignment(1L);
        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(assignment));
        User partnerUser = new User();
        partnerUser.setId(7L);
        when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(activeProfile(7L, 1L)));
        when(assignmentRepository.acceptIfOpen(50L, 7L)).thenReturn(0);

        assertThatThrownBy(() -> deliveryAssignmentService.acceptAssignment(partnerUser, 50L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void acceptAssignmentRejectsPartnerFromDifferentCity() {
        DeliveryAssignment assignment = openAssignment(1L);
        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(assignment));
        User partnerUser = new User();
        partnerUser.setId(7L);
        when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(activeProfile(7L, 2L)));

        assertThatThrownBy(() -> deliveryAssignmentService.acceptAssignment(partnerUser, 50L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void isAssignedPartnerReflectsAcceptedAssignment() {
        DeliveryAssignment assignment = openAssignment(1L);
        assignment.setStatus(DeliveryAssignmentStatus.ACCEPTED);
        User partner = new User();
        partner.setId(7L);
        assignment.setPartner(partner);
        when(assignmentRepository.findByOrderId(1L)).thenReturn(Optional.of(assignment));

        assertThat(deliveryAssignmentService.isAssignedPartner(1L, 7L)).isTrue();
        assertThat(deliveryAssignmentService.isAssignedPartner(1L, 8L)).isFalse();
    }
}
```

- [ ] **Step 4: Run to confirm failure**

Run: `./mvnw test -Dtest=DeliveryAssignmentServiceTest`
Expected: COMPILATION ERROR.

- [ ] **Step 5: Create DeliveryAssignmentService**

Create `src/main/java/com/fooddelivery/delivery/DeliveryAssignmentService.java`:

```java
package com.fooddelivery.delivery;

import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.ForbiddenException;
import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.delivery.dto.AssignmentResponse;
import com.fooddelivery.delivery.event.AssignmentAcceptedEvent;
import com.fooddelivery.order.Order;
import com.fooddelivery.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryAssignmentService {

    private final DeliveryAssignmentRepository assignmentRepository;
    private final DeliveryPartnerProfileRepository profileRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public DeliveryAssignment createOpenAssignment(Order order) {
        DeliveryAssignment assignment = new DeliveryAssignment();
        assignment.setOrder(order);
        assignment.setStatus(DeliveryAssignmentStatus.OPEN);
        return assignmentRepository.save(assignment);
    }

    @Transactional
    public AssignmentResponse acceptAssignment(User partnerUser, Long assignmentId) {
        DeliveryAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("Assignment not found: " + assignmentId));
        DeliveryPartnerProfile profile = profileRepository.findByUserId(partnerUser.getId())
                .orElseThrow(() -> new ForbiddenException("No delivery partner profile for this user"));
        if (!profile.isActive()) {
            throw new ForbiddenException("Delivery partner is not active");
        }
        Long assignmentCityId = assignment.getOrder().getRestaurant().getCity().getId();
        if (!profile.getCity().getId().equals(assignmentCityId)) {
            throw new ForbiddenException("Delivery partner is not eligible for this city");
        }

        int updated = assignmentRepository.acceptIfOpen(assignmentId, partnerUser.getId());
        if (updated == 0) {
            throw new ConflictException("Assignment already taken: " + assignmentId);
        }

        DeliveryAssignment accepted = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("Assignment not found: " + assignmentId));
        eventPublisher.publishEvent(new AssignmentAcceptedEvent(accepted.getOrder().getId(), partnerUser.getId()));
        return toResponse(accepted);
    }

    public List<AssignmentResponse> listOpenAssignments(Long cityId) {
        return assignmentRepository.findOpenByCityId(cityId).stream().map(this::toResponse).toList();
    }

    public boolean isAssignedPartner(Long orderId, Long partnerId) {
        return assignmentRepository.findByOrderId(orderId)
                .map(a -> a.getStatus() == DeliveryAssignmentStatus.ACCEPTED
                        && a.getPartner() != null
                        && a.getPartner().getId().equals(partnerId))
                .orElse(false);
    }

    private AssignmentResponse toResponse(DeliveryAssignment assignment) {
        return new AssignmentResponse(assignment.getId(), assignment.getOrder().getId(), assignment.getStatus(),
                assignment.getPartner() != null ? assignment.getPartner().getId() : null,
                assignment.getOfferedAt(), assignment.getAcceptedAt());
    }
}
```

(`AssignmentAcceptedEvent` is created in Task 10 alongside `OrderPlacedEvent`
and `OrderStatusChangedEvent`; Task 10 also adds the `delivery.event` package.
Until Task 10 exists, this file will not compile — Step 6 below creates a
minimal version of that event class now so Task 9 compiles standalone, and
Task 10 does not need to touch it again.)

- [ ] **Step 6: Create the AssignmentAcceptedEvent now (Task 10 adds its siblings)**

Create `src/main/java/com/fooddelivery/delivery/event/AssignmentAcceptedEvent.java`:

```java
package com.fooddelivery.delivery.event;

public record AssignmentAcceptedEvent(Long orderId, Long partnerId) {
}
```

- [ ] **Step 7: Run the unit test**

Run: `./mvnw test -Dtest=DeliveryAssignmentServiceTest`
Expected: PASS (4 tests).

- [ ] **Step 8: Create the controller**

Create `src/main/java/com/fooddelivery/delivery/DeliveryAssignmentController.java`:

```java
package com.fooddelivery.delivery;

import com.fooddelivery.delivery.dto.AssignmentResponse;
import com.fooddelivery.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/assignments")
@PreAuthorize("hasRole('DELIVERY_PARTNER')")
@RequiredArgsConstructor
public class DeliveryAssignmentController {

    private final DeliveryAssignmentService deliveryAssignmentService;

    @GetMapping("/open")
    public List<AssignmentResponse> listOpen(@RequestParam Long cityId) {
        return deliveryAssignmentService.listOpenAssignments(cityId);
    }

    @PostMapping("/{id}/accept")
    public AssignmentResponse accept(@PathVariable Long id, @AuthenticationPrincipal User partner) {
        return deliveryAssignmentService.acceptAssignment(partner, id);
    }
}
```

- [ ] **Step 9: Wire assignment creation and partner status updates into OrderService**

Modify `src/main/java/com/fooddelivery/order/OrderService.java` — add a
final `DeliveryAssignmentService deliveryAssignmentService` field, then:

Change `acceptOrder` to also create the assignment:

```java
    @Transactional
    public OrderResponse acceptOrder(Long ownerId, Long orderId) {
        Order order = getOrderEntity(orderId);
        verifyRestaurantOwnership(order, ownerId);
        transition(order, OrderStatus.ACCEPTED);
        deliveryAssignmentService.createOpenAssignment(order);
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }
```

Replace the unconditional `else` branch in `updateStatus` with:

```java
        if (caller.getRole() == Role.RESTAURANT_OWNER) {
            verifyRestaurantOwnership(order, caller.getId());
            if (target != OrderStatus.PREPARING) {
                throw new ForbiddenException("Restaurant owners may only mark orders PREPARING via this endpoint");
            }
        } else if (caller.getRole() == Role.DELIVERY_PARTNER) {
            if (!deliveryAssignmentService.isAssignedPartner(orderId, caller.getId())) {
                throw new ForbiddenException("You are not the assigned delivery partner for this order");
            }
            if (target != OrderStatus.OUT_FOR_DELIVERY && target != OrderStatus.DELIVERED) {
                throw new ForbiddenException("Delivery partners may only mark OUT_FOR_DELIVERY or DELIVERED");
            }
        } else {
            throw new ForbiddenException("You are not authorized to update this order's status");
        }
```

- [ ] **Step 10: Update OrderLifecycleServiceTest for the new dependency**

Modify `src/test/java/com/fooddelivery/order/OrderLifecycleServiceTest.java` —
add `@Mock private com.fooddelivery.delivery.DeliveryAssignmentService deliveryAssignmentService;`
as a field. No existing assertions change (the mock returns `null` from
`createOpenAssignment` by default, which `acceptOrder` ignores; `isAssignedPartner`
is never called by the existing owner-only test cases).

- [ ] **Step 11: Run all unit tests**

Run: `./mvnw test -Dtest=OrderLifecycleServiceTest,DeliveryAssignmentServiceTest`
Expected: PASS.

- [ ] **Step 12: Write the integration test for the happy path**

Create `src/test/java/com/fooddelivery/delivery/DeliveryAssignmentControllerIT.java`:

```java
package com.fooddelivery.delivery;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.AbstractIntegrationTest;
import com.fooddelivery.delivery.dto.AssignmentResponse;
import com.fooddelivery.order.OrderStatus;
import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.order.dto.PlaceOrderRequest;
import com.fooddelivery.order.dto.UpdateStatusRequest;
import com.fooddelivery.restaurant.dto.AdminCreateRestaurantRequest;
import com.fooddelivery.restaurant.dto.MenuItemResponse;
import com.fooddelivery.restaurant.dto.MenuItemRequest;
import com.fooddelivery.restaurant.dto.RestaurantResponse;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.LoginRequest;
import com.fooddelivery.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryAssignmentControllerIT extends AbstractIntegrationTest {

    private <T> HttpEntity<T> authed(T body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void partnerAcceptsAssignmentThenDrivesOrderToDelivered() {
        String admin = restTemplate.postForEntity("/auth/login",
                new LoginRequest("admin@fooddelivery.com", "Admin@123"), AuthResponse.class).getBody().token();
        Long cityId = restTemplate.exchange("/cities", HttpMethod.POST,
                authed(new CityRequest("AssignCity", true), admin), CityResponse.class).getBody().id();
        var createReq = new AdminCreateRestaurantRequest(cityId, "AssignRest", "Addr",
                "assign-owner@example.com", "password123");
        Long restaurantId = restTemplate.exchange("/admin/restaurants", HttpMethod.POST,
                authed(createReq, admin), RestaurantResponse.class).getBody().id();
        String ownerToken = restTemplate.postForEntity("/auth/login",
                new LoginRequest("assign-owner@example.com", "password123"), AuthResponse.class).getBody().token();
        Long menuItemId = restTemplate.exchange("/restaurants/" + restaurantId + "/menu-items", HttpMethod.POST,
                authed(new MenuItemRequest("Item", new BigDecimal("40.00"), 5, true), ownerToken),
                MenuItemResponse.class).getBody().id();
        String customerToken = restTemplate.postForEntity("/auth/register",
                new RegisterRequest("assign-cust@example.com", "password123", Role.CUSTOMER, null),
                AuthResponse.class).getBody().token();

        String partnerToken = restTemplate.postForEntity("/auth/register",
                new RegisterRequest("assign-partner@example.com", "password123", Role.DELIVERY_PARTNER, cityId),
                AuthResponse.class).getBody().token();
        var partners = restTemplate.exchange("/admin/delivery-partners", HttpMethod.GET,
                authed(null, admin), com.fooddelivery.delivery.dto.DeliveryPartnerResponse[].class).getBody();
        Long partnerProfileId = partners[0].id();
        restTemplate.exchange("/admin/delivery-partners/" + partnerProfileId, HttpMethod.PUT,
                authed(new com.fooddelivery.delivery.dto.DeliveryPartnerUpdateRequest(cityId, true), admin), Void.class);

        var placeReq = new PlaceOrderRequest(restaurantId, List.of(new PlaceOrderRequest.Item(menuItemId, 1)));
        Long orderId = restTemplate.exchange("/orders", HttpMethod.POST,
                authed(placeReq, customerToken), OrderResponse.class).getBody().id();
        restTemplate.exchange("/orders/" + orderId + "/accept", HttpMethod.POST, authed(null, ownerToken), OrderResponse.class);

        ResponseEntity<AssignmentResponse[]> open = restTemplate.exchange(
                "/assignments/open?cityId=" + cityId, HttpMethod.GET, authed(null, partnerToken), AssignmentResponse[].class);
        assertThat(open.getBody()).hasSize(1);
        Long assignmentId = open.getBody()[0].id();

        ResponseEntity<AssignmentResponse> accepted = restTemplate.exchange(
                "/assignments/" + assignmentId + "/accept", HttpMethod.POST, authed(null, partnerToken), AssignmentResponse.class);
        assertThat(accepted.getBody().status()).isEqualTo(DeliveryAssignmentStatus.ACCEPTED);

        restTemplate.exchange("/orders/" + orderId + "/status", HttpMethod.POST,
                authed(new UpdateStatusRequest(OrderStatus.PREPARING), ownerToken), OrderResponse.class);
        ResponseEntity<OrderResponse> outForDelivery = restTemplate.exchange(
                "/orders/" + orderId + "/status", HttpMethod.POST,
                authed(new UpdateStatusRequest(OrderStatus.OUT_FOR_DELIVERY), partnerToken), OrderResponse.class);
        assertThat(outForDelivery.getBody().status()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);

        ResponseEntity<OrderResponse> delivered = restTemplate.exchange(
                "/orders/" + orderId + "/status", HttpMethod.POST,
                authed(new UpdateStatusRequest(OrderStatus.DELIVERED), partnerToken), OrderResponse.class);
        assertThat(delivered.getBody().status()).isEqualTo(OrderStatus.DELIVERED);
    }
}
```

- [ ] **Step 13: Write the assignment contention concurrency test**

Create `src/test/java/com/fooddelivery/delivery/DeliveryAssignmentConcurrencyIT.java`:

```java
package com.fooddelivery.delivery;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.AbstractIntegrationTest;
import com.fooddelivery.delivery.dto.AssignmentResponse;
import com.fooddelivery.delivery.dto.DeliveryPartnerResponse;
import com.fooddelivery.delivery.dto.DeliveryPartnerUpdateRequest;
import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.order.dto.PlaceOrderRequest;
import com.fooddelivery.restaurant.dto.AdminCreateRestaurantRequest;
import com.fooddelivery.restaurant.dto.MenuItemRequest;
import com.fooddelivery.restaurant.dto.MenuItemResponse;
import com.fooddelivery.restaurant.dto.RestaurantResponse;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.LoginRequest;
import com.fooddelivery.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryAssignmentConcurrencyIT extends AbstractIntegrationTest {

    private <T> HttpEntity<T> authed(T body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void onlyOnePartnerWinsConcurrentAcceptRace() throws InterruptedException {
        String admin = restTemplate.postForEntity("/auth/login",
                new LoginRequest("admin@fooddelivery.com", "Admin@123"), AuthResponse.class).getBody().token();
        Long cityId = restTemplate.exchange("/cities", HttpMethod.POST,
                authed(new CityRequest("RaceCity", true), admin), CityResponse.class).getBody().id();
        var createReq = new AdminCreateRestaurantRequest(cityId, "RaceRest", "Addr",
                "race-owner@example.com", "password123");
        Long restaurantId = restTemplate.exchange("/admin/restaurants", HttpMethod.POST,
                authed(createReq, admin), RestaurantResponse.class).getBody().id();
        String ownerToken = restTemplate.postForEntity("/auth/login",
                new LoginRequest("race-owner@example.com", "password123"), AuthResponse.class).getBody().token();
        Long menuItemId = restTemplate.exchange("/restaurants/" + restaurantId + "/menu-items", HttpMethod.POST,
                authed(new MenuItemRequest("Item", new BigDecimal("15.00"), 100, true), ownerToken),
                MenuItemResponse.class).getBody().id();
        String customerToken = restTemplate.postForEntity("/auth/register",
                new RegisterRequest("race-cust@example.com", "password123", Role.CUSTOMER, null),
                AuthResponse.class).getBody().token();

        int partnerCount = 10;
        List<String> partnerTokens = new ArrayList<>();
        for (int i = 0; i < partnerCount; i++) {
            String token = restTemplate.postForEntity("/auth/register",
                    new RegisterRequest("racer-partner" + i + "@example.com", "password123", Role.DELIVERY_PARTNER, cityId),
                    AuthResponse.class).getBody().token();
            partnerTokens.add(token);
        }
        DeliveryPartnerResponse[] profiles = restTemplate.exchange("/admin/delivery-partners", HttpMethod.GET,
                authed(null, admin), DeliveryPartnerResponse[].class).getBody();
        for (DeliveryPartnerResponse profile : profiles) {
            restTemplate.exchange("/admin/delivery-partners/" + profile.id(), HttpMethod.PUT,
                    authed(new DeliveryPartnerUpdateRequest(cityId, true), admin), Void.class);
        }

        var placeReq = new PlaceOrderRequest(restaurantId, List.of(new PlaceOrderRequest.Item(menuItemId, 1)));
        Long orderId = restTemplate.exchange("/orders", HttpMethod.POST,
                authed(placeReq, customerToken), OrderResponse.class).getBody().id();
        restTemplate.exchange("/orders/" + orderId + "/accept", HttpMethod.POST, authed(null, ownerToken), OrderResponse.class);

        Long assignmentId = restTemplate.exchange("/assignments/open?cityId=" + cityId, HttpMethod.GET,
                authed(null, partnerTokens.get(0)), AssignmentResponse[].class).getBody()[0].id();

        ExecutorService pool = Executors.newFixedThreadPool(partnerCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (String token : partnerTokens) {
            futures.add(pool.submit(() -> {
                try {
                    startLatch.await();
                    ResponseEntity<String> response = restTemplate.exchange(
                            "/assignments/" + assignmentId + "/accept", HttpMethod.POST, authed(null, token), String.class);
                    if (response.getStatusCode() == HttpStatus.OK) {
                        successCount.incrementAndGet();
                    } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        startLatch.countDown();
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
        pool.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(partnerCount - 1);
    }
}
```

- [ ] **Step 14: Run all tests (requires Docker running)**

Run: `./mvnw test`
Expected: PASS — the concurrency test proves exactly one partner ever wins
the race for a given assignment.

- [ ] **Step 15: Commit**

```bash
git add src/main/java/com/fooddelivery/delivery src/main/java/com/fooddelivery/order/OrderService.java src/test/java/com/fooddelivery/delivery src/test/java/com/fooddelivery/order/OrderLifecycleServiceTest.java
git commit -m "Add delivery partner assignment with accept-race contention handling"
```

---

### Task 10: Asynchronous Notification Fan-out

**Files:**
- Create: `src/main/java/com/fooddelivery/order/event/OrderPlacedEvent.java`
- Create: `src/main/java/com/fooddelivery/order/event/OrderStatusChangedEvent.java`
- Modify: `src/main/java/com/fooddelivery/order/OrderService.java`
- Modify: `src/test/java/com/fooddelivery/order/OrderServiceTest.java`
- Modify: `src/test/java/com/fooddelivery/order/OrderLifecycleServiceTest.java`
- Create: `src/main/java/com/fooddelivery/notification/Notification.java`
- Create: `src/main/java/com/fooddelivery/notification/NotificationRepository.java`
- Create: `src/main/java/com/fooddelivery/notification/dto/NotificationResponse.java`
- Create: `src/main/java/com/fooddelivery/notification/NotificationService.java`
- Create: `src/main/java/com/fooddelivery/notification/NotificationEventListener.java`
- Create: `src/main/java/com/fooddelivery/notification/NotificationController.java`
- Test: `src/test/java/com/fooddelivery/notification/NotificationServiceTest.java`
- Test: `src/test/java/com/fooddelivery/notification/NotificationFanOutIT.java`

**Interfaces:**
- Consumes: `Order`/`OrderRepository`/`OrderStatus` (Task 7); `AssignmentAcceptedEvent` (Task 9, already published); `DeliveryAssignmentRepository` (Task 9); `notificationExecutor` bean (Task 2); `NotFoundException` (Task 2).
- Produces: `Notification` (`Long id`, `User recipient`, `Order order`, `String message`, `boolean read`, `Instant createdAt`); no later task consumes this directly — it is the terminal fan-out task.

- [ ] **Step 1: Create the order events**

Create `src/main/java/com/fooddelivery/order/event/OrderPlacedEvent.java`:

```java
package com.fooddelivery.order.event;

public record OrderPlacedEvent(Long orderId) {
}
```

Create `src/main/java/com/fooddelivery/order/event/OrderStatusChangedEvent.java`:

```java
package com.fooddelivery.order.event;

import com.fooddelivery.order.OrderStatus;

public record OrderStatusChangedEvent(Long orderId, OrderStatus oldStatus, OrderStatus newStatus) {
}
```

- [ ] **Step 2: Publish events from OrderService**

Modify `src/main/java/com/fooddelivery/order/OrderService.java` — add a
final `ApplicationEventPublisher eventPublisher` field (import
`org.springframework.context.ApplicationEventPublisher`,
`com.fooddelivery.order.event.OrderPlacedEvent`,
`com.fooddelivery.order.event.OrderStatusChangedEvent`).

At the end of `placeOrder`, right before `return toResponse(order, orderItems);`, add:

```java
        eventPublisher.publishEvent(new OrderPlacedEvent(order.getId()));
```

Change `transition` to capture and publish the previous status:

```java
    private void transition(Order order, OrderStatus target) {
        Set<OrderStatus> allowed = TRANSITIONS.getOrDefault(order.getStatus(), Set.of());
        if (!allowed.contains(target)) {
            throw new ConflictException(
                    "Cannot transition order " + order.getId() + " from " + order.getStatus() + " to " + target);
        }
        OrderStatus previous = order.getStatus();
        order.setStatus(target);
        order.setUpdatedAt(java.time.Instant.now());
        eventPublisher.publishEvent(new OrderStatusChangedEvent(order.getId(), previous, target));
    }
```

- [ ] **Step 3: Update existing OrderService tests for the new dependency**

Modify `src/test/java/com/fooddelivery/order/OrderServiceTest.java` — add
`@Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;`
as a field.

Modify `src/test/java/com/fooddelivery/order/OrderLifecycleServiceTest.java` —
add the same mock field.

- [ ] **Step 4: Run existing order tests to confirm they still pass**

Run: `./mvnw test -Dtest=OrderServiceTest,OrderLifecycleServiceTest`
Expected: PASS (event publishing to a mock publisher is a no-op).

- [ ] **Step 5: Write the failing unit test for NotificationService**

Create `src/test/java/com/fooddelivery/notification/NotificationServiceTest.java`:

```java
package com.fooddelivery.notification;

import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.delivery.DeliveryAssignmentRepository;
import com.fooddelivery.order.Order;
import com.fooddelivery.order.OrderRepository;
import com.fooddelivery.order.OrderStatus;
import com.fooddelivery.restaurant.Restaurant;
import com.fooddelivery.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private DeliveryAssignmentRepository assignmentRepository;
    @InjectMocks private NotificationService notificationService;

    private Order orderWithParties(Long customerId, Long ownerId) {
        User customer = new User();
        customer.setId(customerId);
        User owner = new User();
        owner.setId(ownerId);
        Restaurant restaurant = new Restaurant();
        restaurant.setOwner(owner);
        Order order = new Order();
        order.setId(1L);
        order.setCustomer(customer);
        order.setRestaurant(restaurant);
        return order;
    }

    @Test
    void notifyOrderPlacedNotifiesCustomerAndOwner() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(orderWithParties(10L, 20L)));
        when(assignmentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

        notificationService.notifyOrderPlaced(1L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        List<Long> recipientIds = captor.getAllValues().stream().map(n -> n.getRecipient().getId()).toList();
        assertThat(recipientIds).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    void notifyOrderStatusChangedIncludesAssignedPartnerWhenPresent() {
        Order order = orderWithParties(10L, 20L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        com.fooddelivery.delivery.DeliveryAssignment assignment = new com.fooddelivery.delivery.DeliveryAssignment();
        assignment.setStatus(com.fooddelivery.delivery.DeliveryAssignmentStatus.ACCEPTED);
        User partner = new User();
        partner.setId(30L);
        assignment.setPartner(partner);
        when(assignmentRepository.findByOrderId(1L)).thenReturn(Optional.of(assignment));

        notificationService.notifyOrderStatusChanged(1L, OrderStatus.PREPARING, OrderStatus.OUT_FOR_DELIVERY);

        verify(notificationRepository, times(3)).save(any(Notification.class));
    }

    @Test
    void markReadRejectsWrongRecipient() {
        when(notificationRepository.findByIdAndRecipientId(5L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(99L, 5L))
                .isInstanceOf(NotFoundException.class);
    }
}
```

- [ ] **Step 6: Run to confirm failure**

Run: `./mvnw test -Dtest=NotificationServiceTest`
Expected: COMPILATION ERROR — `notification` package doesn't exist yet.

- [ ] **Step 7: Create the entity, repository, and DTO**

Create `src/main/java/com/fooddelivery/notification/Notification.java`:

```java
package com.fooddelivery.notification;

import com.fooddelivery.order.Order;
import com.fooddelivery.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recipient_id")
    private User recipient;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
```

Create `src/main/java/com/fooddelivery/notification/NotificationRepository.java`:

```java
package com.fooddelivery.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);
    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);
}
```

Create `src/main/java/com/fooddelivery/notification/dto/NotificationResponse.java`:

```java
package com.fooddelivery.notification.dto;

import java.time.Instant;

public record NotificationResponse(Long id, Long orderId, String message, boolean read, Instant createdAt) {
}
```

- [ ] **Step 8: Create NotificationService**

Create `src/main/java/com/fooddelivery/notification/NotificationService.java`:

```java
package com.fooddelivery.notification;

import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.delivery.DeliveryAssignment;
import com.fooddelivery.delivery.DeliveryAssignmentRepository;
import com.fooddelivery.delivery.DeliveryAssignmentStatus;
import com.fooddelivery.notification.dto.NotificationResponse;
import com.fooddelivery.order.Order;
import com.fooddelivery.order.OrderRepository;
import com.fooddelivery.order.OrderStatus;
import com.fooddelivery.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final OrderRepository orderRepository;
    private final DeliveryAssignmentRepository assignmentRepository;

    @Transactional
    public void notifyOrderPlaced(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        String message = "Order #" + orderId + " has been placed.";
        save(order.getCustomer(), order, message);
        save(order.getRestaurant().getOwner(), order, "New order #" + orderId + " received.");
    }

    @Transactional
    public void notifyOrderStatusChanged(Long orderId, OrderStatus oldStatus, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        String message = "Order #" + orderId + " changed from " + oldStatus + " to " + newStatus + ".";
        save(order.getCustomer(), order, message);
        save(order.getRestaurant().getOwner(), order, message);
        assignedPartner(orderId).ifPresent(partner -> save(partner, order, message));
    }

    @Transactional
    public void notifyAssignmentAccepted(Long orderId, Long partnerId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        String message = "A delivery partner has picked up order #" + orderId + ".";
        save(order.getCustomer(), order, message);
        save(order.getRestaurant().getOwner(), order, message);
    }

    public List<NotificationResponse> getMyNotifications(Long userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found: " + notificationId));
        notification.setRead(true);
        return toResponse(notification);
    }

    private Optional<User> assignedPartner(Long orderId) {
        return assignmentRepository.findByOrderId(orderId)
                .filter(a -> a.getStatus() == DeliveryAssignmentStatus.ACCEPTED)
                .map(DeliveryAssignment::getPartner);
    }

    private void save(User recipient, Order order, String message) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setOrder(order);
        notification.setMessage(message);
        notificationRepository.save(notification);
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getOrder().getId(),
                notification.getMessage(), notification.isRead(), notification.getCreatedAt());
    }
}
```

- [ ] **Step 9: Run the unit test**

Run: `./mvnw test -Dtest=NotificationServiceTest`
Expected: PASS (3 tests).

- [ ] **Step 10: Create the event listener and controller**

Create `src/main/java/com/fooddelivery/notification/NotificationEventListener.java`:

```java
package com.fooddelivery.notification;

import com.fooddelivery.delivery.event.AssignmentAcceptedEvent;
import com.fooddelivery.order.event.OrderPlacedEvent;
import com.fooddelivery.order.event.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        notificationService.notifyOrderPlaced(event.orderId());
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        notificationService.notifyOrderStatusChanged(event.orderId(), event.oldStatus(), event.newStatus());
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAssignmentAccepted(AssignmentAcceptedEvent event) {
        notificationService.notifyAssignmentAccepted(event.orderId(), event.partnerId());
    }
}
```

Create `src/main/java/com/fooddelivery/notification/NotificationController.java`:

```java
package com.fooddelivery.notification;

import com.fooddelivery.notification.dto.NotificationResponse;
import com.fooddelivery.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> myNotifications(@AuthenticationPrincipal User user) {
        return notificationService.getMyNotifications(user.getId());
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return notificationService.markRead(user.getId(), id);
    }
}
```

- [ ] **Step 11: Write the async fan-out integration test**

Create `src/test/java/com/fooddelivery/notification/NotificationFanOutIT.java`:

```java
package com.fooddelivery.notification;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.AbstractIntegrationTest;
import com.fooddelivery.notification.dto.NotificationResponse;
import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.order.dto.PlaceOrderRequest;
import com.fooddelivery.restaurant.dto.AdminCreateRestaurantRequest;
import com.fooddelivery.restaurant.dto.MenuItemRequest;
import com.fooddelivery.restaurant.dto.MenuItemResponse;
import com.fooddelivery.restaurant.dto.RestaurantResponse;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.LoginRequest;
import com.fooddelivery.user.dto.RegisterRequest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationFanOutIT extends AbstractIntegrationTest {

    private <T> HttpEntity<T> authed(T body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void placingAnOrderEventuallyNotifiesCustomerAndOwner() {
        String admin = restTemplate.postForEntity("/auth/login",
                new LoginRequest("admin@fooddelivery.com", "Admin@123"), AuthResponse.class).getBody().token();
        Long cityId = restTemplate.exchange("/cities", HttpMethod.POST,
                authed(new CityRequest("NotifyCity", true), admin), CityResponse.class).getBody().id();
        var createReq = new AdminCreateRestaurantRequest(cityId, "NotifyRest", "Addr",
                "notify-owner@example.com", "password123");
        Long restaurantId = restTemplate.exchange("/admin/restaurants", HttpMethod.POST,
                authed(createReq, admin), RestaurantResponse.class).getBody().id();
        String ownerToken = restTemplate.postForEntity("/auth/login",
                new LoginRequest("notify-owner@example.com", "password123"), AuthResponse.class).getBody().token();
        Long menuItemId = restTemplate.exchange("/restaurants/" + restaurantId + "/menu-items", HttpMethod.POST,
                authed(new MenuItemRequest("Item", new BigDecimal("20.00"), 5, true), ownerToken),
                MenuItemResponse.class).getBody().id();
        String customerToken = restTemplate.postForEntity("/auth/register",
                new RegisterRequest("notify-cust@example.com", "password123", Role.CUSTOMER, null),
                AuthResponse.class).getBody().token();

        var placeReq = new PlaceOrderRequest(restaurantId, List.of(new PlaceOrderRequest.Item(menuItemId, 1)));
        restTemplate.exchange("/orders", HttpMethod.POST, authed(placeReq, customerToken), OrderResponse.class);

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            ResponseEntity<NotificationResponse[]> customerNotifs = restTemplate.exchange(
                    "/notifications", HttpMethod.GET, authed(null, customerToken), NotificationResponse[].class);
            assertThat(customerNotifs.getBody()).isNotEmpty();

            ResponseEntity<NotificationResponse[]> ownerNotifs = restTemplate.exchange(
                    "/notifications", HttpMethod.GET, authed(null, ownerToken), NotificationResponse[].class);
            assertThat(ownerNotifs.getBody()).isNotEmpty();
        });
    }
}
```

- [ ] **Step 12: Run all tests (requires Docker running)**

Run: `./mvnw test`
Expected: PASS.

- [ ] **Step 13: Commit**

```bash
git add src/main/java/com/fooddelivery/order src/main/java/com/fooddelivery/notification src/test/java/com/fooddelivery/order src/test/java/com/fooddelivery/notification
git commit -m "Add asynchronous notification fan-out on order and assignment events"
```

---

### Task 11: Restaurant Ratings with Rolling Average

**Files:**
- Create: `src/main/java/com/fooddelivery/rating/Rating.java`
- Create: `src/main/java/com/fooddelivery/rating/RatingRepository.java`
- Create: `src/main/java/com/fooddelivery/rating/dto/RateRequest.java`
- Create: `src/main/java/com/fooddelivery/rating/dto/RatingResponse.java`
- Create: `src/main/java/com/fooddelivery/rating/RatingService.java`
- Create: `src/main/java/com/fooddelivery/rating/RatingController.java`
- Test: `src/test/java/com/fooddelivery/rating/RatingServiceTest.java`
- Test: `src/test/java/com/fooddelivery/rating/RatingControllerIT.java`

**Interfaces:**
- Consumes: `Order`/`OrderRepository`/`OrderStatus` (Task 7); `Restaurant`/`RestaurantRepository` (Task 5); `NotFoundException`/`ConflictException`/`ForbiddenException` (Task 2).
- Produces: `Rating` entity; no later task depends on it — this is the final domain feature task.

- [ ] **Step 1: Create the entity, repository, and DTOs**

Create `src/main/java/com/fooddelivery/rating/Rating.java`:

```java
package com.fooddelivery.rating;

import com.fooddelivery.order.Order;
import com.fooddelivery.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "ratings")
@Getter
@Setter
@NoArgsConstructor
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(optional = false)
    @JoinColumn(name = "rater_id")
    private User rater;

    @Column(nullable = false)
    private int score;

    @Column(length = 2000)
    private String review;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
```

Create `src/main/java/com/fooddelivery/rating/RatingRepository.java`:

```java
package com.fooddelivery.rating;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {
    Optional<Rating> findByOrderId(Long orderId);
}
```

Create `src/main/java/com/fooddelivery/rating/dto/RateRequest.java`:

```java
package com.fooddelivery.rating.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record RateRequest(@Min(1) @Max(5) int score, @Size(max = 2000) String review) {
}
```

Create `src/main/java/com/fooddelivery/rating/dto/RatingResponse.java`:

```java
package com.fooddelivery.rating.dto;

import java.time.Instant;

public record RatingResponse(Long id, Long orderId, Long restaurantId, int score, String review, Instant createdAt) {
}
```

- [ ] **Step 2: Write the failing unit test**

Create `src/test/java/com/fooddelivery/rating/RatingServiceTest.java`:

```java
package com.fooddelivery.rating;

import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.ForbiddenException;
import com.fooddelivery.order.Order;
import com.fooddelivery.order.OrderRepository;
import com.fooddelivery.order.OrderStatus;
import com.fooddelivery.rating.dto.RateRequest;
import com.fooddelivery.rating.dto.RatingResponse;
import com.fooddelivery.restaurant.Restaurant;
import com.fooddelivery.restaurant.RestaurantRepository;
import com.fooddelivery.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock private RatingRepository ratingRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @InjectMocks private RatingService ratingService;

    private Order deliveredOrderFor(Long customerId, Restaurant restaurant) {
        User customer = new User();
        customer.setId(customerId);
        Order order = new Order();
        order.setId(1L);
        order.setCustomer(customer);
        order.setRestaurant(restaurant);
        order.setStatus(OrderStatus.DELIVERED);
        return order;
    }

    @Test
    void ratingUpdatesRunningAverage() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        restaurant.setAvgRating(BigDecimal.ZERO);
        restaurant.setRatingCount(0);
        Order order = deliveredOrderFor(1L, restaurant);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(ratingRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(ratingRepository.save(any(Rating.class))).thenAnswer(inv -> inv.getArgument(0));
        User customer = new User();
        customer.setId(1L);

        RatingResponse response = ratingService.rate(customer, 1L, new RateRequest(4, "Great food"));

        assertThat(response.score()).isEqualTo(4);
        assertThat(restaurant.getAvgRating()).isEqualByComparingTo("4.00");
        assertThat(restaurant.getRatingCount()).isEqualTo(1);
    }

    @Test
    void ratingRejectsNonOwningCustomer() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        Order order = deliveredOrderFor(1L, restaurant);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        User otherCustomer = new User();
        otherCustomer.setId(2L);

        assertThatThrownBy(() -> ratingService.rate(otherCustomer, 1L, new RateRequest(5, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void ratingRejectsNonDeliveredOrder() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        Order order = deliveredOrderFor(1L, restaurant);
        order.setStatus(OrderStatus.PREPARING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        User customer = new User();
        customer.setId(1L);

        assertThatThrownBy(() -> ratingService.rate(customer, 1L, new RateRequest(5, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void ratingRejectsDuplicateForSameOrder() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        Order order = deliveredOrderFor(1L, restaurant);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(ratingRepository.findByOrderId(1L)).thenReturn(Optional.of(new Rating()));
        User customer = new User();
        customer.setId(1L);

        assertThatThrownBy(() -> ratingService.rate(customer, 1L, new RateRequest(5, null)))
                .isInstanceOf(ConflictException.class);
    }
}
```

- [ ] **Step 3: Run to confirm failure**

Run: `./mvnw test -Dtest=RatingServiceTest`
Expected: COMPILATION ERROR.

- [ ] **Step 4: Create RatingService**

Create `src/main/java/com/fooddelivery/rating/RatingService.java`:

```java
package com.fooddelivery.rating;

import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.ForbiddenException;
import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.order.Order;
import com.fooddelivery.order.OrderRepository;
import com.fooddelivery.order.OrderStatus;
import com.fooddelivery.rating.dto.RateRequest;
import com.fooddelivery.rating.dto.RatingResponse;
import com.fooddelivery.restaurant.Restaurant;
import com.fooddelivery.restaurant.RestaurantRepository;
import com.fooddelivery.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;
    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional
    public RatingResponse rate(User customer, Long orderId, RateRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new ForbiddenException("You may only rate your own orders");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new ConflictException("Only delivered orders can be rated");
        }
        if (ratingRepository.findByOrderId(orderId).isPresent()) {
            throw new ConflictException("Order already rated: " + orderId);
        }

        Rating rating = new Rating();
        rating.setOrder(order);
        rating.setRater(customer);
        rating.setScore(request.score());
        rating.setReview(request.review());
        rating = ratingRepository.save(rating);

        Restaurant restaurant = order.getRestaurant();
        int newCount = restaurant.getRatingCount() + 1;
        BigDecimal totalScore = restaurant.getAvgRating()
                .multiply(BigDecimal.valueOf(restaurant.getRatingCount()))
                .add(BigDecimal.valueOf(request.score()));
        restaurant.setAvgRating(totalScore.divide(BigDecimal.valueOf(newCount), 2, RoundingMode.HALF_UP));
        restaurant.setRatingCount(newCount);
        restaurantRepository.save(restaurant);

        return new RatingResponse(rating.getId(), order.getId(), restaurant.getId(),
                rating.getScore(), rating.getReview(), rating.getCreatedAt());
    }
}
```

- [ ] **Step 5: Run the unit test**

Run: `./mvnw test -Dtest=RatingServiceTest`
Expected: PASS (4 tests).

- [ ] **Step 6: Create RatingController**

Create `src/main/java/com/fooddelivery/rating/RatingController.java`:

```java
package com.fooddelivery.rating;

import com.fooddelivery.rating.dto.RateRequest;
import com.fooddelivery.rating.dto.RatingResponse;
import com.fooddelivery.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders/{orderId}/ratings")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    @PostMapping
    public ResponseEntity<RatingResponse> rate(@PathVariable Long orderId,
                                                @AuthenticationPrincipal User customer,
                                                @Valid @RequestBody RateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ratingService.rate(customer, orderId, request));
    }
}
```

- [ ] **Step 7: Write the integration test (full lifecycle to DELIVERED, then rate)**

Create `src/test/java/com/fooddelivery/rating/RatingControllerIT.java`:

```java
package com.fooddelivery.rating;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.AbstractIntegrationTest;
import com.fooddelivery.delivery.dto.AssignmentResponse;
import com.fooddelivery.delivery.dto.DeliveryPartnerResponse;
import com.fooddelivery.delivery.dto.DeliveryPartnerUpdateRequest;
import com.fooddelivery.order.OrderStatus;
import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.order.dto.PlaceOrderRequest;
import com.fooddelivery.order.dto.UpdateStatusRequest;
import com.fooddelivery.rating.dto.RateRequest;
import com.fooddelivery.rating.dto.RatingResponse;
import com.fooddelivery.restaurant.dto.AdminCreateRestaurantRequest;
import com.fooddelivery.restaurant.dto.MenuItemRequest;
import com.fooddelivery.restaurant.dto.MenuItemResponse;
import com.fooddelivery.restaurant.dto.RestaurantResponse;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.LoginRequest;
import com.fooddelivery.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RatingControllerIT extends AbstractIntegrationTest {

    private <T> HttpEntity<T> authed(T body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void customerRatesDeliveredOrderAndRestaurantAverageUpdates() {
        String admin = restTemplate.postForEntity("/auth/login",
                new LoginRequest("admin@fooddelivery.com", "Admin@123"), AuthResponse.class).getBody().token();
        Long cityId = restTemplate.exchange("/cities", HttpMethod.POST,
                authed(new CityRequest("RateCity", true), admin), CityResponse.class).getBody().id();
        var createReq = new AdminCreateRestaurantRequest(cityId, "RateRest", "Addr",
                "rate-owner@example.com", "password123");
        Long restaurantId = restTemplate.exchange("/admin/restaurants", HttpMethod.POST,
                authed(createReq, admin), RestaurantResponse.class).getBody().id();
        String ownerToken = restTemplate.postForEntity("/auth/login",
                new LoginRequest("rate-owner@example.com", "password123"), AuthResponse.class).getBody().token();
        Long menuItemId = restTemplate.exchange("/restaurants/" + restaurantId + "/menu-items", HttpMethod.POST,
                authed(new MenuItemRequest("Item", new BigDecimal("18.00"), 5, true), ownerToken),
                MenuItemResponse.class).getBody().id();
        String customerToken = restTemplate.postForEntity("/auth/register",
                new RegisterRequest("rate-cust@example.com", "password123", Role.CUSTOMER, null),
                AuthResponse.class).getBody().token();
        String partnerToken = restTemplate.postForEntity("/auth/register",
                new RegisterRequest("rate-partner@example.com", "password123", Role.DELIVERY_PARTNER, cityId),
                AuthResponse.class).getBody().token();
        DeliveryPartnerResponse[] partners = restTemplate.exchange("/admin/delivery-partners", HttpMethod.GET,
                authed(null, admin), DeliveryPartnerResponse[].class).getBody();
        restTemplate.exchange("/admin/delivery-partners/" + partners[0].id(), HttpMethod.PUT,
                authed(new DeliveryPartnerUpdateRequest(cityId, true), admin), Void.class);

        var placeReq = new PlaceOrderRequest(restaurantId, List.of(new PlaceOrderRequest.Item(menuItemId, 1)));
        Long orderId = restTemplate.exchange("/orders", HttpMethod.POST,
                authed(placeReq, customerToken), OrderResponse.class).getBody().id();
        restTemplate.exchange("/orders/" + orderId + "/accept", HttpMethod.POST, authed(null, ownerToken), OrderResponse.class);
        Long assignmentId = restTemplate.exchange("/assignments/open?cityId=" + cityId, HttpMethod.GET,
                authed(null, partnerToken), AssignmentResponse[].class).getBody()[0].id();
        restTemplate.exchange("/assignments/" + assignmentId + "/accept", HttpMethod.POST, authed(null, partnerToken), AssignmentResponse.class);
        restTemplate.exchange("/orders/" + orderId + "/status", HttpMethod.POST,
                authed(new UpdateStatusRequest(OrderStatus.PREPARING), ownerToken), OrderResponse.class);
        restTemplate.exchange("/orders/" + orderId + "/status", HttpMethod.POST,
                authed(new UpdateStatusRequest(OrderStatus.OUT_FOR_DELIVERY), partnerToken), OrderResponse.class);
        restTemplate.exchange("/orders/" + orderId + "/status", HttpMethod.POST,
                authed(new UpdateStatusRequest(OrderStatus.DELIVERED), partnerToken), OrderResponse.class);

        ResponseEntity<RatingResponse> rated = restTemplate.exchange(
                "/orders/" + orderId + "/ratings", HttpMethod.POST,
                authed(new RateRequest(5, "Excellent"), customerToken), RatingResponse.class);
        assertThat(rated.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<RestaurantResponse> restaurant = restTemplate.getForEntity(
                "/restaurants/" + restaurantId, RestaurantResponse.class);
        assertThat(restaurant.getBody().avgRating()).isEqualByComparingTo("5.00");
        assertThat(restaurant.getBody().ratingCount()).isEqualTo(1);

        ResponseEntity<String> duplicate = restTemplate.exchange(
                "/orders/" + orderId + "/ratings", HttpMethod.POST,
                authed(new RateRequest(3, "second try"), customerToken), String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
```

- [ ] **Step 8: Run all tests (requires Docker running)**

Run: `./mvnw test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/fooddelivery/rating src/test/java/com/fooddelivery/rating
git commit -m "Add restaurant ratings with rolling average"
```

---

### Task 12: Final Documentation and Full-Suite Verification

**Files:**
- Modify: `README.md`
- Test: (none new — this task runs the complete existing suite)

**Interfaces:**
- Consumes: the complete API surface built in Tasks 1-11.
- Produces: nothing further in code — this is the plan's final task.

- [ ] **Step 1: Run the complete test suite**

Run: `./mvnw test`
Expected: PASS — every unit and integration test from Tasks 1-11 green in
one run (requires Docker running for Testcontainers).

- [ ] **Step 2: Write the final README**

Replace the contents of `README.md` with:

```markdown
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

Java 21, Spring Boot 3.3 (Web, Data JPA, Security, Validation), PostgreSQL,
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
```

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "Finalize README with run instructions and design summary"
```
