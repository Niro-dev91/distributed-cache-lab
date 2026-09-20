# Day 01 — Building the Spring Boot + PostgreSQL Foundation

## Distributed Cache Lab

This is Day 01 of my **Distributed Cache Lab** learning project.

The goal of this project is not only to learn Redis, but to understand the real problems that appear when caching is used inside distributed applications.

During the next stages, this project will cover:

- Redis distributed caching
- Cache-aside pattern
- Cache HIT and MISS
- TTL and cache expiration
- Cache invalidation
- Multiple Spring Boot instances
- Correlation IDs
- MDC logging
- Global exception handling
- Redis failure handling
- Graceful degradation
- Centralized logging
- Cache stampede
- Distributed locking
- TTL jitter

For Day 01, we start with the most important component:

> **The database and REST API that will act as the source of truth.**

---

## 1. The Problem

Imagine we have a Product API:

```text
GET /api/products/1
```

Without caching, every request travels to PostgreSQL:

```text
Client
   |
   v
Spring Boot
   |
   v
PostgreSQL
```

If the same product is requested repeatedly:

```text
GET /api/products/1
GET /api/products/1
GET /api/products/1
GET /api/products/1
```

the application performs repeated database reads.

Later we will introduce Redis to reduce unnecessary database access.

But before adding a cache, we need a clean database-backed API.

---

## 2. Day 01 Goal

By the end of Day 01, the application should support:

```text
POST   /api/products
GET    /api/products/{id}
GET    /api/products
PUT    /api/products/{id}
DELETE /api/products/{id}
```

The architecture is intentionally simple:

```text
Client
   |
   v
ProductController
   |
   v
ProductService
   |
   v
ProductRepository
   |
   v
PostgreSQL
```

At this stage, every read goes directly to PostgreSQL.

That gives us a clean baseline before adding Redis.

---

## 3. Technology Stack

For Day 01:

- Java 21
- Spring Boot
- Spring Web
- Spring Data JPA
- Hibernate
- PostgreSQL
- Maven
- Jakarta Validation

---

## 4. Project Structure

```text
distributed-cache-lab/
│
├── src/
│   └── main/
│       ├── java/com/niro/cachelab/
│       │   ├── controller/
│       │   │   └── ProductController.java
│       │   │
│       │   ├── dto/
│       │   │   ├── ProductRequest.java
│       │   │   └── ProductResponse.java
│       │   │
│       │   ├── entity/
│       │   │   └── Product.java
│       │   │
│       │   ├── repository/
│       │   │   └── ProductRepository.java
│       │   │
│       │   ├── service/
│       │   │   └── ProductService.java
│       │   │
│       │   └── DistributedCacheLabApplication.java
│       │
│       └── resources/
│           └── application.properties
│
├── docs/
│   └── day-01.md
│
└── pom.xml
```

---

## 5. Create the PostgreSQL Database

Create a database:

```sql
CREATE DATABASE distributed_cache_db;
```

The application will use PostgreSQL as the main source of truth.

Later Redis will only act as a performance layer in front of this database.

---

## 6. Database Configuration

Add the PostgreSQL configuration to:

```text
src/main/resources/application.properties
```

```properties
spring.application.name=distributed-cache-lab

server.port=8080

# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/distributed_cache_db
spring.datasource.username=postgres
spring.datasource.password=YOUR_PASSWORD

spring.datasource.driver-class-name=org.postgresql.Driver

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.open-in-view=false
```

Replace:

```text
YOUR_PASSWORD
```

with your local PostgreSQL password.

---

## 7. Product Entity

The `Product` entity represents data stored in PostgreSQL.

```java
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer quantity;

    protected Product() {
    }

    public Product(
            String name,
            BigDecimal price,
            Integer quantity
    ) {
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void update(
            String name,
            BigDecimal price,
            Integer quantity
    ) {
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }
}
```

Example database data:

```text
id | name             | price  | quantity
------------------------------------------
1  | MacBook Pro      | 450000 | 5
2  | Gaming Keyboard  | 25000  | 20
```

---

## 8. Request DTO

Instead of accepting the entity directly from the API, the application uses a request DTO.

```java
public record ProductRequest(

        @NotBlank
        String name,

        @NotNull
        @Positive
        BigDecimal price,

        @NotNull
        @Positive
        Integer quantity

) {
}
```

This allows request validation before data reaches the service layer.

Invalid example:

```json
{
  "name": "",
  "price": -100,
  "quantity": 0
}
```

Later in the project, validation errors will be handled using a global exception handler.

---

## 9. Response DTO

The application also uses a separate response object.

```java
public record ProductResponse(
        Long id,
        String name,
        BigDecimal price,
        Integer quantity
) {
}
```

This keeps the API contract separate from the database entity.

---

## 10. Repository Layer

Spring Data JPA provides the repository implementation automatically.

```java
public interface ProductRepository
        extends JpaRepository<Product, Long> {
}
```

This immediately provides methods such as:

```text
save()
findById()
findAll()
delete()
existsById()
```

without manually writing SQL for basic CRUD operations.

---

## 11. Service Layer

The service layer contains the business logic.

Example read flow:

```java
@Transactional(readOnly = true)
public ProductResponse getProduct(Long id) {

    log.info(
            "Fetching product productId={}",
            id
    );

    Product product =
            productRepository
                    .findById(id)
                    .orElseThrow();

    return mapToResponse(product);
}
```

At this point the important thing is:

```text
Every GET request reaches PostgreSQL.
```

That behavior will change once Redis is introduced.

---

## 12. REST Controller

The controller exposes the Product API.

```text
POST   /api/products
GET    /api/products/{id}
GET    /api/products
PUT    /api/products/{id}
DELETE /api/products/{id}
```

Example controller endpoint:

```java
@GetMapping("/{id}")
public ResponseEntity<ProductResponse> getProduct(
        @PathVariable Long id
) {

    return ResponseEntity.ok(
            productService.getProduct(id)
    );
}
```

The controller stays thin while the business logic remains in the service layer.

---

## 13. Create a Product

Request:

```http
POST /api/products
```

Body:

```json
{
  "name": "MacBook Pro",
  "price": 450000,
  "quantity": 5
}
```

Expected response:

```json
{
  "id": 1,
  "name": "MacBook Pro",
  "price": 450000,
  "quantity": 5
}
```

Flow:

```text
POST /api/products
        |
        v
ProductController
        |
        v
ProductService
        |
        v
ProductRepository
        |
        v
PostgreSQL
```

---

## 14. Read a Product

Request:

```http
GET /api/products/1
```

Response:

```json
{
  "id": 1,
  "name": "MacBook Pro",
  "price": 450000,
  "quantity": 5
}
```

Current flow:

```text
GET /api/products/1
        |
        v
ProductController
        |
        v
ProductService
        |
        v
ProductRepository
        |
        v
PostgreSQL
        |
        v
Response
```

If this endpoint is called five times:

```text
Request 1 → PostgreSQL
Request 2 → PostgreSQL
Request 3 → PostgreSQL
Request 4 → PostgreSQL
Request 5 → PostgreSQL
```

This is exactly the behavior we will improve with Redis.

---

## 15. Basic Logging

I also added simple service logs from the beginning.

Examples:

```text
INFO Creating product name=MacBook Pro

INFO Product created productId=1

INFO Fetching product productId=1

INFO Product updated productId=1

INFO Product deleted productId=1
```

These are basic logs for now.

Later they will evolve into logs containing:

```text
correlationId
instanceId
cache status
request duration
error code
```

For example:

```text
correlationId=ABC123
instance=instance-a
CACHE_MISS productId=1
```

---

## 16. Why Start Without Redis?

It may be tempting to add Redis immediately.

But first having a normal database-backed system makes the caching behavior easier to understand.

Today:

```text
Request
   |
   v
PostgreSQL
```

Later:

```text
Request
   |
   v
Redis
 |   |
HIT MISS
 |   |
 |   v
 | PostgreSQL
 |   |
 +---+
```

This gives us something measurable to improve.

---

## 17. Key Design Decision — PostgreSQL Is the Source of Truth

One of the most important decisions in this project is:

```text
PostgreSQL = source of truth
Redis      = performance optimization
```

If Redis loses data, the product still exists in PostgreSQL.

That decision will become important later when we simulate:

```text
Redis outage
cache corruption
cache expiration
stale cache
```

The application should always know where the authoritative data lives.

---

## 18. Day 01 Result

At the end of Day 01 we have:

```text
Client
   |
   v
Spring Boot
   |
   +-- ProductController
   |
   +-- ProductService
   |
   +-- ProductRepository
   |
   v
PostgreSQL
```

And the API supports:

```text
CREATE product
READ product
READ all products
UPDATE product
DELETE product
```

---

## 19. What I Learned

Day 01 covered:

- Spring Boot project structure
- REST controller design
- Service layer
- Repository layer
- Spring Data JPA
- PostgreSQL integration
- JPA entities
- DTOs
- Jakarta Validation
- Transaction basics
- Basic application logging
- Source-of-truth design

The main takeaway is:

> Before introducing a cache, first establish a reliable source of truth and understand the normal request flow.

---

## 20. Current Limitation

Every product read still hits PostgreSQL.

For example:

```text
1000 requests
      |
      v
1000 database reads
```

For frequently accessed data, this creates unnecessary database load.

That gives us the problem for Day 02.

---

## Next — Day 02

In Day 02, Redis will be introduced using the **cache-aside pattern**.

The flow will change from:

```text
Spring Boot
    |
    v
PostgreSQL
```

to:

```text
Spring Boot
    |
    v
Redis
 |   |
HIT MISS
 |   |
 |   v
 | PostgreSQL
 |   |
 | CACHE_WRITE
 |   |
 +---+
```

We will implement and test:

```text
CACHE_MISS
DB_FETCH
CACHE_WRITE
CACHE_HIT
```

The goal is to make repeated product requests avoid unnecessary PostgreSQL reads.

---

## Series

- **Day 01** — Spring Boot + PostgreSQL Foundation
- **Day 02** — Redis + Cache-Aside
- **Day 03** — TTL + Cache Metrics
- **Day 04** — Cache Invalidation
- **Day 05** — Multiple Application Instances
- **Day 06** — Correlation IDs + MDC Logging
- **Day 07** — Global Error Handling
- **Day 08** — Redis Failure + Graceful Degradation
- **Day 09** — Centralized Logging with ELK
- **Day 10** — Cache Stampede + Distributed Lock

---

## Repository

Project:

```text
distributed-cache-lab
```

Recommended location for this file:

```text
docs/day-01.md
```
