# Day 02 — Adding Redis and Implementing the Cache-Aside Pattern

## Distributed Cache Lab

This is Day 02 of my **Distributed Cache Lab** learning project.

In Day 01, I built a simple Spring Boot Product API backed by PostgreSQL.

The architecture was:

```text
Client
   |
   v
Spring Boot
   |
   v
PostgreSQL
```

Every read request went directly to the database.

That works, but it creates unnecessary database load when the same data is requested repeatedly.

Today, I introduced Redis and implemented the **cache-aside pattern**.

---

## 1. The Problem

Suppose this endpoint is called many times:

```http
GET /api/products/1
```

Without caching:

```text
Request 1 → PostgreSQL
Request 2 → PostgreSQL
Request 3 → PostgreSQL
Request 4 → PostgreSQL
Request 5 → PostgreSQL
```

Even if the product has not changed, the application keeps reading the same data from the database.

This creates unnecessary database work.

The goal is to reduce repeated database access.

---

## 2. Day 02 Goal

Today I wanted the application to behave like this:

```text
GET /api/products/1
        |
        v
      Redis
      /   \
    HIT   MISS
     |      |
     |   PostgreSQL
     |      |
     |   CACHE_WRITE
     |      |
     +------+
        |
        v
     Response
```

The important events are:

```text
CACHE_MISS
DB_FETCH
CACHE_WRITE
CACHE_HIT
```

---

## 3. What Is the Cache-Aside Pattern?

The cache-aside pattern means the application controls the cache explicitly.

The flow is:

```text
1. Check Redis
2. If value exists → return cached value
3. If value does not exist → read PostgreSQL
4. Store the result in Redis
5. Return the result
```

Redis does not automatically fetch data from PostgreSQL.

The application decides when to read and write the cache.

---

## 4. Architecture After Adding Redis

The project now looks like:

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
ProductCacheService
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
   |
   v
Response
```

PostgreSQL is still the **source of truth**.

Redis is only used to reduce repeated database reads.

---

## 5. Add Redis Dependency

The project needs Spring Data Redis.

Add this dependency to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

---

## 6. Run Redis Locally

For this project, Redis runs locally using Docker.

Example:

```bash
docker run --name redis-server -p 63790:6379 -d redis:8-alpine
```

Check that the container is running:

```bash
docker ps
```

Test Redis:

```bash
docker exec -it redis-server redis-cli
```

Then:

```text
PING
```

Expected result:

```text
PONG
```

---

## 7. Redis Configuration

Add this to:

```text
src/main/resources/application.properties
```

```properties
# Redis
spring.data.redis.host=localhost
spring.data.redis.port=63790
spring.data.redis.database=0

spring.data.redis.connect-timeout=2s
spring.data.redis.timeout=2s
```

Now Spring Boot connects to:

```text
Redis → localhost:63790
```

The Docker container is named:

```text
redis-server
```

The container exposes Redis's internal port `6379` through local port `63790`:

```text
localhost:63790 → redis-server:6379
```

Because the Spring Boot application is running directly on the local machine, it connects using `localhost:63790`.

---

## 8. Redis Key Design

Instead of using a key like:

```text
1
```

I use:

```text
product:1
```

This makes the cache easier to understand and avoids collisions later.

For example:

```text
product:1
product:2
user:1
order:1
portfolio:1
```

A simple naming convention makes Redis easier to debug.

---

## 9. Store Product Data as JSON

The application stores product data in Redis as JSON.

Example key:

```text
product:1
```

Example value:

```json
{
  "id": 1,
  "name": "MacBook Pro",
  "price": 450000,
  "quantity": 5
}
```

For this project, I used:

```text
StringRedisTemplate
```

and Jackson's:

```text
ObjectMapper
```

to serialize and deserialize the cached data.

---

## 10. ProductCacheService

I created a dedicated cache service.

```text
ProductCacheService
```

Its responsibility is to:

```text
read cache
write cache
build Redis keys
log HIT / MISS
```

Example cache read:

```java
public Optional<ProductResponse> get(Long productId) {

    String key = buildKey(productId);

    String cachedProduct =
            redisTemplate
                    .opsForValue()
                    .get(key);

    if (cachedProduct == null) {

        log.info(
                "CACHE_MISS productId={}",
                productId
        );

        return Optional.empty();
    }

    try {

        ProductResponse product =
                objectMapper.readValue(
                        cachedProduct,
                        ProductResponse.class
                );

        log.info(
                "CACHE_HIT productId={}",
                productId
        );

        return Optional.of(product);

    } catch (JsonProcessingException exception) {

        throw new IllegalStateException(
                "Failed to read product from cache",
                exception
        );
    }
}
```

---

## 11. Writing Data to Redis

The cache service also stores database results in Redis.

```java
public void put(
        Long productId,
        ProductResponse product
) {

    String key = buildKey(productId);

    try {

        String json =
                objectMapper.writeValueAsString(product);

        redisTemplate
                .opsForValue()
                .set(
                        key,
                        json,
                        Duration.ofMinutes(5)
                );

        log.info(
                "CACHE_WRITE productId={}",
                productId
        );

    } catch (JsonProcessingException exception) {

        throw new IllegalStateException(
                "Failed to write product to cache",
                exception
        );
    }
}
```

The cached value also gets a TTL.

For now:

```text
TTL = 5 minutes
```

TTL will be explored properly in Day 03.

---

## 12. Update ProductService

The service now checks Redis before PostgreSQL.

```java
@Transactional(readOnly = true)
public ProductResponse getProduct(Long id) {

    log.info(
            "Fetching product productId={}",
            id
    );

    var cachedProduct =
            productCacheService.get(id);

    if (cachedProduct.isPresent()) {

        return cachedProduct.get();
    }

    log.info(
            "DB_FETCH productId={}",
            id
    );

    Product product =
            productRepository
                    .findById(id)
                    .orElseThrow();

    ProductResponse response =
            mapToResponse(product);

    productCacheService.put(
            id,
            response
    );

    return response;
}
```

This is the main cache-aside logic.

---

## 13. First Request — Cache MISS

Assume Redis is empty.

Call:

```http
GET /api/products/1
```

Flow:

```text
Client
   |
   v
Spring Boot
   |
   v
Redis
   |
 MISS
   |
   v
PostgreSQL
   |
   v
Product
   |
   v
CACHE_WRITE
   |
   v
Response
```

Expected logs:

```text
Fetching product productId=1

CACHE_MISS productId=1

DB_FETCH productId=1

CACHE_WRITE productId=1
```

PostgreSQL is used because the cache did not contain the product.

---

## 14. Second Request — Cache HIT

Call the same endpoint again:

```http
GET /api/products/1
```

Now Redis contains:

```text
product:1
```

Flow:

```text
Client
   |
   v
Spring Boot
   |
   v
Redis
   |
  HIT
   |
   v
Response
```

Expected log:

```text
CACHE_HIT productId=1
```

The important difference is:

```text
No PostgreSQL query
```

The product is returned from Redis.

---

## 15. Verify Redis Manually

Open Redis CLI:

```bash
docker exec -it redis-server redis-cli
```

List product keys:

```text
KEYS product:*
```

Example:

```text
1) "product:1"
```

Read the cached value:

```text
GET product:1
```

Example:

```json
{"id":1,"name":"MacBook Pro","price":450000.00,"quantity":5}
```

---

## 16. Delete the Cached Key Manually

Run:

```text
DEL product:1
```

Then call:

```http
GET /api/products/1
```

The logs should return to:

```text
CACHE_MISS
DB_FETCH
CACHE_WRITE
```

This clearly demonstrates how the cache-aside pattern works.

---

## 17. Why Redis Reduces Database Load

Without Redis:

```text
5 requests
   |
   v
5 database queries
```

With Redis:

```text
Request 1
   |
CACHE_MISS
   |
Database
   |
CACHE_WRITE

Request 2 → CACHE_HIT
Request 3 → CACHE_HIT
Request 4 → CACHE_HIT
Request 5 → CACHE_HIT
```

Database queries:

```text
1
```

instead of:

```text
5
```

This is the main performance benefit.

---

## 18. Important Design Decision

The application still treats:

```text
PostgreSQL = source of truth
```

and:

```text
Redis = temporary cached copy
```

This means Redis can expire or lose cached values without losing the actual product data.

That decision becomes important later when handling Redis failures.

---

## 19. A New Problem Appears

Caching improves performance, but it introduces another problem.

Suppose Redis contains:

```text
product:1
price = 450000
```

Then PostgreSQL is updated:

```text
product:1
price = 475000
```

Redis may still contain:

```text
450000
```

The API could return old data.

This is called:

```text
Stale Cache
```

We will solve that in a later step using cache invalidation.

---

## 20. Another Problem — Cache Expiration

The cached value should not remain forever.

That is why we use:

```text
TTL
```

TTL means:

```text
Time To Live
```

Example:

```text
product:1
TTL = 300 seconds
```

After 300 seconds, Redis automatically removes the key.

Day 03 will focus on:

```text
TTL
cache expiration
cache hit ratio
cache metrics
```

---

## 21. Day 02 Result

The architecture now looks like:

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
              ProductCacheService
                       |
                       v
                     Redis
                  /         \
               HIT           MISS
                |             |
                |             v
                |         PostgreSQL
                |             |
                |        CACHE_WRITE
                |             |
                +-------------+
                       |
                       v
                    Response
```

---

## 22. What I Learned

Day 02 covered:

- Redis basics
- Spring Data Redis
- `StringRedisTemplate`
- Redis key design
- JSON serialization
- Cache-aside pattern
- Cache HIT
- Cache MISS
- Cache WRITE
- Reducing database load
- Redis as a performance layer
- PostgreSQL as the source of truth

The main takeaway is:

> A cache should reduce unnecessary database work, but adding a cache also introduces new consistency and expiration problems that need to be handled carefully.

---

## 23. Logs to Remember

The most important Day 02 flow is:

```text
CACHE_MISS productId=1
DB_FETCH productId=1
CACHE_WRITE productId=1
```

Then:

```text
CACHE_HIT productId=1
```

That is the simplest way to prove the cache is working.

---

## Next — Day 03

In Day 03, the focus will be:

```text
Redis TTL
Cache expiration
Cache hit ratio
Cache miss ratio
Cache metrics
```

We will make the TTL configurable and track how useful the cache actually is.

The next flow will be:

```text
CACHE_WRITE
    |
    v
TTL countdown
    |
    v
Cache expires
    |
    v
Next request = CACHE_MISS
```

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


