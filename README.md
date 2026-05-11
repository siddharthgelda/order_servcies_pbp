# 📦 Order Service — Help & Developer Guide

> Quick reference for understanding, running, debugging, and extending the Order Service (`order_servcies_pbp`).

**GitHub:** https://github.com/siddharthgelda/order_servcies_pbp  
**Group:** `com.pbp.ecomm` · **Artifact:** `order` · **Version:** `0.0.1`  
**Stack:** Java 21 · Spring Boot 3.3.2 · Spring Cloud 2023.0.3 · MySQL · JWT

---

## Table of Contents

- [What This Service Does](#what-this-service-does)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Running the Application](#running-the-application)
- [Order Lifecycle](#order-lifecycle)
- [API Quick Reference](#api-quick-reference)
- [Business Rules](#business-rules)
- [Security Model](#security-model)
- [Service Dependencies](#service-dependencies)
- [Common Errors & Fixes](#common-errors--fixes)
- [Known Issues in build.gradle](#known-issues-in-buildgradle)
- [Extending the Service](#extending-the-service)
- [Pending / TODO Items](#pending--todo-items)

---

## What This Service Does

The Order Service is a standalone Spring Boot microservice in the `com.pbp.ecomm` e-commerce platform. It handles:

- Placing new customer orders (validates stock via Product Service using OpenFeign)
- Calculating order totals: subtotal, 18% GST, and shipping charges
- Managing full order status lifecycle from PENDING through to DELIVERED or CANCELLED
- Customer order history and tracking
- Admin-level order management (view all, update status, filter by status)
- JWT-based stateless security — user identity always from token, never from request body

---

## Tech Stack

| Layer                 | Technology                     | Version     |
|-----------------------|--------------------------------|-------------|
| Language              | Java                           | 21          |
| Framework             | Spring Boot                    | 3.3.2       |
| Service Communication | Spring Cloud OpenFeign         | 2023.0.3    |
| Build Tool            | Gradle                         | 8.x         |
| Database              | MySQL                          | (managed)   |
| ORM                   | Spring Data JPA (Hibernate)    | (managed)   |
| Security              | Spring Security + JWT          | jjwt 0.11.5 |
| Mapping               | MapStruct                      | 1.5.5.Final |
| Boilerplate           | Lombok                         | (managed)   |
| Validation            | Spring Boot Starter Validation | (managed)   |
| Testing               | JUnit 5, Spring Security Test  | (managed)   |

---

## Project Structure

```
src/main/java/com/pbp/ecomm/order/
├── OrderController.java              ← REST endpoints (/api/v1/orders)
├── services/
│   └── OrderService.java             ← All business logic
├── model/
│   ├── Order.java                    ← JPA entity (aggregate root)
│   ├── OrderItem.java                ← JPA entity (child of Order)
│   ├── OrderStatus.java              ← Enum: PENDING, CONFIRMED, PROCESSING,
│   │                                    SHIPPED, DELIVERED, CANCELLED
│   └── ShippingAddress.java          ← Embeddable shipping address
├── dto/
│   ├── CreateOrderRequest.java       ← Incoming: place new order
│   ├── CancelOrderRequest.java       ← Incoming: cancel with reason
│   ├── UpdateOrderStatusRequest.java ← Incoming: admin status update
│   ├── OrderDTO.java                 ← Full order response
│   ├── OrderSummaryDTO.java          ← Lightweight paginated response
│   ├── OrderTrackingDTO.java         ← Tracking info response
│   └── ProductSnapshot.java          ← Response model from Product Service
├── mapper/
│   └── OrderMapper.java              ← MapStruct: entity ↔ DTO conversions
├── repo/
│   └── OrderRepository.java          ← Spring Data JPA custom queries
├── client/
│   └── ProductClient.java            ← OpenFeign client → Product Service
└── filter/
    └── AuthenticatedUser.java        ← JWT principal wrapper

src/main/resources/
└── application.yml                   ← App configuration
```

---

## Prerequisites

| Tool            | Version                                              |
|-----------------|------------------------------------------------------|
| Java (JDK)      | 21+                                                  |
| Gradle          | 8.x (or use `./gradlew`)                             |
| MySQL           | 8.x                                                  |
| Product Service | Running and accessible (required for order creation) |

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/siddharthgelda/order_servcies_pbp.git
cd order_servcies_pbp
```

### 2. Create the MySQL database

```sql
CREATE DATABASE order_db;
CREATE USER 'order_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON order_db.* TO 'order_user'@'localhost';
FLUSH PRIVILEGES;
```

### 3. Configure application.yml

```yaml
server:
  port: 8081   # Use a different port from Product Service

spring:
  application:
    name: order-service
  datasource:
    url: jdbc:mysql://localhost:3306/order_db
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

app:
  jwt:
    secret: ${JWT_SECRET}
    expiration-ms: 86400000

# OpenFeign — Product Service URL
product-service:
  url: http://localhost:8080
```

### 4. Build

```bash
./gradlew clean build
```

---

## Configuration

### Environment Variables

| Variable      | Description                                             |
|---------------|---------------------------------------------------------|
| `DB_USERNAME` | MySQL username                                          |
| `DB_PASSWORD` | MySQL password                                          |
| `JWT_SECRET`  | Shared JWT secret key (must match Auth/Product service) |

### OpenFeign Client Setup

The `ProductClient` uses Spring Cloud OpenFeign to call the Product Service. Make sure `@EnableFeignClients` is present
on your main application class:

```java

@SpringBootApplication
@EnableFeignClients
public class OrderApplication { ...
}
```

And configure the Product Service base URL in `application.yml`:

```yaml
product-service:
  url: http://localhost:8080  # or Eureka service name if using service discovery
```

---

## Running the Application

### Using Gradle

```bash
# Default run
./gradlew bootRun

# With specific profile
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Using JAR

```bash
./gradlew bootJar
java -jar build/libs/order-0.0.1.jar
```

### Using Docker (optional)

```dockerfile
FROM eclipse-temurin:21-jre
COPY build/libs/order-0.0.1.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```bash
docker build -t order-service:latest .
docker run -p 8081:8081 \
  -e DB_USERNAME=order_user \
  -e DB_PASSWORD=your_password \
  -e JWT_SECRET=your_shared_secret \
  order-service:latest
```

---

## Order Lifecycle

```
  [Customer Places Order]
           │
           ▼
        PENDING  ◄─── cancellable
           │
           ▼  (Admin confirms)
       CONFIRMED  ◄─── cancellable
           │
           ▼  (Warehouse picks & packs)
       PROCESSING  ◄─── cancellable
           │
           ▼  (Dispatched — trackingNumber, courierName, expectedDeliveryDate set)
        SHIPPED  ✗ not cancellable
           │
           ▼  (deliveredAt timestamp set automatically)
       DELIVERED  ✗ not cancellable
           │
    ┌──────┘
    │  (From PENDING / CONFIRMED / PROCESSING only)
    ▼
  CANCELLED  (cancellationReason + cancelledAt set)
```

**Cancellable statuses:** `PENDING`, `CONFIRMED`, `PROCESSING`

---

## API Quick Reference

Base URL: `http://localhost:8081/api/v1/orders`

| Method  | Path             | Role             | Description                        |
|---------|------------------|------------------|------------------------------------|
| `POST`  | `/`              | Customer         | Place a new order                  |
| `GET`   | `/{id}`          | Customer / Admin | Get full order detail              |
| `GET`   | `/my-orders`     | Customer         | Paginated personal order history   |
| `POST`  | `/{id}/cancel`   | Customer         | Cancel an order                    |
| `GET`   | `/`              | Admin only       | List all orders (filter by status) |
| `GET`   | `/user/{userId}` | Admin only       | All orders for a specific user     |
| `PATCH` | `/{id}/status`   | Admin only       | Update order status                |

### Place an Order

```bash
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Authorization: Bearer <jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      { "productId": "prod-uuid-123", "quantity": 2 }
    ],
    "shippingAddress": {
      "line1": "123 MG Road",
      "city": "Pune",
      "state": "Maharashtra",
      "pincode": "411001"
    },
    "paymentMethod": "UPI",
    "customerNotes": "Leave at door"
  }'
```

### Cancel an Order

```bash
curl -X POST http://localhost:8081/api/v1/orders/{id}/cancel \
  -H "Authorization: Bearer <jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{ "reason": "Changed my mind" }'
```

### Update Order Status (Admin)

```bash
curl -X PATCH http://localhost:8081/api/v1/orders/{id}/status \
  -H "Authorization: Bearer <admin_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "SHIPPED",
    "trackingNumber": "BD123456789IN",
    "courierName": "BlueDart",
    "expectedDeliveryDate": "2026-05-18"
  }'
```

### Filter All Orders by Status (Admin)

```bash
curl "http://localhost:8081/api/v1/orders?status=PENDING&page=0&size=20" \
  -H "Authorization: Bearer <admin_jwt_token>"
```

---

## Business Rules

### Pricing & Tax

| Component | Rule                                                                   |
|-----------|------------------------------------------------------------------------|
| Subtotal  | `unitPrice × quantity` summed for all items                            |
| Shipping  | Free if subtotal ≥ ₹500, otherwise ₹49.00                              |
| GST       | 18% of subtotal, rounded HALF_UP to 2 decimal places                   |
| Total     | `subtotal + shippingCharge + taxAmount`                                |
| Discount  | Field exists (`discountAmount`), defaults to `0` — not yet implemented |

### Stock Validation (via ProductClient)

Before creating an order, for each item the service calls the Product Service to check:

- `inStock == true`
- `stockQuantity >= requestedQuantity`

If either check fails, a `RuntimeException` is thrown and the order is rejected.

### Cancellation Rules

- Only the **order owner** may cancel (service enforces ownership — not just controller)
- Cancellation only allowed from `PENDING`, `CONFIRMED`, or `PROCESSING` status
- A `reason` string is required
- `cancelledAt` timestamp is set automatically on cancellation

### Admin Status Update Rules

- Moving to `SHIPPED` → `trackingNumber`, `courierName`, `expectedDeliveryDate` should be provided in the request
- Moving to `DELIVERED` → `deliveredAt` is set automatically to `LocalDateTime.now()`

---

## Security Model

| Concern                 | Implementation                                                                     |
|-------------------------|------------------------------------------------------------------------------------|
| Authentication          | JWT — stateless, validated on every request                                        |
| User identity           | Always from `@AuthenticationPrincipal AuthenticatedUser` (never from request body) |
| Admin endpoints         | Protected with `@PreAuthorize("hasRole('ADMIN')")`                                 |
| Customer data isolation | `findByIdAndUserId` ensures customers only see their own orders                    |
| Shared JWT secret       | Must be the same across Order Service, Product Service, and Auth Service           |

---

## Service Dependencies

```
┌─────────────────┐        OpenFeign        ┌──────────────────┐
│  Order Service  │ ──────────────────────▶ │  Product Service │
│  (port 8081)    │   getProductSnapshot()   │  (port 8080)     │
└─────────────────┘                          └──────────────────┘
         │
         ▼
      MySQL DB
   (order_db)
```

The Order Service calls the Product Service synchronously via OpenFeign on every order creation. If the Product Service
is down, order creation will fail. A circuit breaker (Resilience4j) is recommended — see TODOs.

---

## Common Errors & Fixes

| Error                                          | Cause                                          | Fix                                                                                              |
|------------------------------------------------|------------------------------------------------|--------------------------------------------------------------------------------------------------|
| `Order not found: <id>`                        | Wrong ID, or order belongs to a different user | Verify the order ID and JWT user ownership                                                       |
| `Insufficient stock for product: <name>`       | Product Service reports low or zero stock      | Check inventory in Product Service before retrying                                               |
| `Order cannot be cancelled in status: SHIPPED` | Order already dispatched                       | Cancellation not allowed once shipped                                                            |
| `feign.FeignException` / connection refused    | Product Service is down or wrong URL           | Check Product Service is running; verify `product-service.url` in config                         |
| `IllegalArgumentException` on status filter    | Invalid status string in `GET /?status=`       | Use exact enum values: `PENDING`, `CONFIRMED`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED` |
| `AccessDeniedException` on admin endpoints     | JWT does not carry `ROLE_ADMIN`                | Ensure token is generated with the `ADMIN` role                                                  |
| `io.jsonwebtoken.security.SignatureException`  | JWT secret mismatch between services           | Ensure all services share the same `JWT_SECRET` env variable                                     |

---

## Known Issues in build.gradle

The current `build.gradle` has some duplicate entries that should be cleaned up:

```groovy
// These are duplicated — remove extras:
implementation 'org.mapstruct:mapstruct:1.5.5.Final'          // declared twice
annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'  // declared 3 times
annotationProcessor 'org.projectlombok:lombok-mapstruct-binding:0.2.0'  // declared twice

// This is declared twice — keep only one:
version = '0.0.1'
```

**Clean version of the dependencies block:**

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
    implementation 'com.mysql:mysql-connector-j'

    implementation 'org.mapstruct:mapstruct:1.5.5.Final'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'
    annotationProcessor 'org.projectlombok:lombok-mapstruct-binding:0.2.0'

    implementation 'io.jsonwebtoken:jjwt-api:0.11.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.11.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.11.5'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
}
```

---

## Extending the Service

### Add Kafka Events

Kafka is already stubbed in `OrderService` as comments. To enable:

1. Add to `build.gradle`:
   ```groovy
   implementation 'org.springframework.kafka:spring-kafka'
   ```
2. Inject `KafkaTemplate<String, Object> kafkaTemplate` in `OrderService`
3. Uncomment the `kafkaTemplate.send(...)` calls in `create()`, `cancel()`, `updateStatus()`
4. Create event classes: `OrderCreatedEvent`, `OrderCancelledEvent`, `OrderStatusChangedEvent`

**Topics to publish to:**

- `order-events` — consumed by Payment Service, Inventory Service, Notification Service

### Add Order Status History

1. Create `OrderStatusHistory` JPA entity and `OrderStatusHistoryRepository`
2. Uncomment `historyRepo` injection and `saveHistory()` calls in `OrderService`
3. Uncomment `getStatusHistory()` method in `OrderService`
4. Add `GET /api/v1/orders/{id}/history` endpoint in `OrderController`

### Add Circuit Breaker for ProductClient

```groovy
// build.gradle
implementation 'io.github.resilience4j:resilience4j-spring-boot3'
```

```java
// ProductClient.java
@CircuitBreaker(name = "productService", fallbackMethod = "fallbackSnapshot")
ProductSnapshot getProductSnapshot(String productId);

default ProductSnapshot fallbackSnapshot(String productId, Throwable t) {
    throw new RuntimeException("Product Service unavailable, please try again later");
}
```

### Add Service Discovery (Eureka)

```groovy
implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
```

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

---

## Gradle Quick Reference

| Command                         | Description                 |
|---------------------------------|-----------------------------|
| `./gradlew clean build`         | Full clean build with tests |
| `./gradlew clean build -x test` | Build skipping tests        |
| `./gradlew bootRun`             | Run locally                 |
| `./gradlew bootJar`             | Package executable JAR      |
| `./gradlew test`                | Run all tests               |
| `./gradlew dependencies`        | View full dependency tree   |

---

*Order Service · com.pbp.ecomm · Spring Boot 3.3.2 · Spring Cloud 2023.0.3 · Java 21*
