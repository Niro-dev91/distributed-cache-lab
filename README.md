# Distributed Cache Lab

A hands-on project for learning distributed caching and resilience using Java, Spring Boot, Redis, PostgreSQL, and centralized logging.

## Architecture

```text
                    Client
                       |
               -----------------
               |               |
               v               v
        Spring Boot A    Spring Boot B
            :8081            :8082
               |               |
               +-------+-------+
                       |
                       v
                     Redis
                       |
                       v
                  PostgreSQL
```

Logs are centralized through:

```text
Spring Boot
    ↓
Structured JSON Logs
    ↓
Logstash
    ↓
Elasticsearch
    ↓
Kibana
```

## Tech Stack

* Java 21
* Spring Boot
* Spring Data JPA
* PostgreSQL
* Redis
* Spring Data Redis
* Docker
* MDC Logging
* Elasticsearch
* Logstash
* Kibana

## Concepts Covered

* Cache-aside pattern
* Cache HIT / MISS
* Redis TTL
* Cache invalidation
* Stale cache handling
* Distributed caching
* Multiple application instances
* Correlation IDs
* MDC logging
* Global exception handling
* Graceful degradation
* Redis failure handling
* Centralized logging
* Cache stampede
* Distributed locking
* TTL jitter

## Learning Journey

### Day 1

Spring Boot Product API with PostgreSQL.

### Day 2

Redis integration and cache-aside pattern.

### Day 3

TTL, cache expiry, cache metrics, and hit ratio.

### Day 4

Cache invalidation and stale data handling.

### Day 5

Multiple Spring Boot instances sharing Redis.

### Day 6

Correlation IDs and MDC logging.

### Day 7

Global exception handling with `@RestControllerAdvice`.

### Day 8

Redis failure handling and graceful degradation.

### Day 9

Centralized logging with ELK.

### Day 10

Cache stampede prevention using Redis distributed locks and TTL jitter.

## Example Request Flow

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
     Response
```

## Redis Failure

```text
Request
   |
   v
Redis
   |
   X
   |
   v
PostgreSQL
   |
   v
Response
```

Redis is treated as an optional performance dependency while PostgreSQL remains the source of truth.

## Cache Stampede Protection

```text
Multiple Requests
        |
        v
    CACHE MISS
        |
        v
 Redis Distributed Lock
      /        \
 acquired      busy
    |            |
    v            v
 Database       wait
    |
    v
 CACHE_WRITE
```
## Status

🚧 Project under active development and learning.
