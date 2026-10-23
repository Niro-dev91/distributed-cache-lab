# Day 04 — Cache Invalidation

## Goal

Day 04 solves the **stale cache problem**.

Example:

```text
PostgreSQL = latest value
Redis      = old value
```

If Redis keeps the old value, the API may return outdated data.

The solution is:

```text
Update database
      ↓
Delete Redis key
      ↓
Next GET = CACHE_MISS
      ↓
Read latest value from PostgreSQL
      ↓
CACHE_WRITE
```

PostgreSQL remains the **source of truth**.

---

## Redis Setup

```properties
spring.data.redis.host=localhost
spring.data.redis.port=63790
spring.data.redis.database=0
```

Redis container:

```text
redis-server
```

---

## Add Cache Eviction

Add this to `ProductCacheService`:

```java
public void evict(Long productId) {

    String key = buildKey(productId);

    Boolean deleted =
            redisTemplate.delete(key);

    if (Boolean.TRUE.equals(deleted)) {

        log.info(
                "CACHE_EVICT productId={}",
                productId
        );
    }
}
```

The cache service now supports:

```text
get()
put()
evict()
```

---

## Update Product

After updating PostgreSQL, delete the old Redis entry.

```java
@Transactional
public ProductResponse updateProduct(
        Long id,
        ProductRequest request
) {

    Product product =
            productRepository
                    .findById(id)
                    .orElseThrow();

    product.update(
            request.name(),
            request.price(),
            request.quantity()
    );

    Product updatedProduct =
            productRepository.save(product);

    productCacheService.evict(id);

    return mapToResponse(updatedProduct);
}
```

Flow:

```text
PUT /api/products/1
        ↓
PostgreSQL UPDATE
        ↓
CACHE_EVICT
```

---

## Delete Product

Also remove the Redis key when deleting a product.

```java
@Transactional
public void deleteProduct(Long id) {

    Product product =
            productRepository
                    .findById(id)
                    .orElseThrow();

    productRepository.delete(product);

    productCacheService.evict(id);
}
```

Flow:

```text
DELETE /api/products/1
        ↓
PostgreSQL DELETE
        ↓
CACHE_EVICT
```

---

## Quick Test

First:

```http
GET /api/products/1
```

Expected:

```text
CACHE_MISS
DB_FETCH
CACHE_WRITE
```

Second GET:

```text
CACHE_HIT
```

Now update:

```http
PUT /api/products/1
```

Expected:

```text
CACHE_EVICT productId=1
```

Next GET:

```text
CACHE_MISS
DB_FETCH
CACHE_WRITE
```

The latest PostgreSQL value is now cached.

---

## Verify Redis

Open Redis CLI:

```bash
docker exec -it redis-server redis-cli
```

Check:

```text
EXISTS product:1
```

After update/delete, expected:

```text
(integer) 0
```

---

## Why Database First?

Preferred order:

```text
Update PostgreSQL
      ↓
Delete Redis
```

This is safer than deleting Redis first because another request could otherwise reload the old database value back into the cache.

---

## Key Concepts

- Stale cache
- Cache invalidation
- Cache eviction
- Database as source of truth
- Update consistency
- Delete consistency

---

## Why Add an Eviction Metric?

The eviction metric is added for **observability**.

It shows how often cached values are removed because products are updated or deleted.

```text
HIT      → data came from Redis
MISS     → Redis did not contain the data
WRITE    → data was added to Redis
EVICTION → cached data was intentionally removed
```

Tracking evictions helps us understand cache behavior and identify situations where frequent updates may be causing a low cache hit ratio or unnecessary cache rebuilding.

The metric is not required for cache invalidation itself; it is mainly useful for monitoring and troubleshooting.