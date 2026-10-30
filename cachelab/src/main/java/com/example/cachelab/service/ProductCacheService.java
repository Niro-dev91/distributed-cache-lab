package com.example.cachelab.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.example.cachelab.dto.ProductResponse;
import com.example.cachelab.service.CacheMetricsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class ProductCacheService {

        private static final Logger log = LoggerFactory.getLogger(ProductCacheService.class);

        private static final String CACHE_PREFIX = "product:";

        private final Duration CACHE_TTL;

        private final StringRedisTemplate redisTemplate;

        private final ObjectMapper objectMapper;

        private final CacheMetricsService cacheMetricsService;

        private final String instanceId;

        public ProductCacheService(
                        StringRedisTemplate redisTemplate,
                        ObjectMapper objectMapper,
                        CacheMetricsService cacheMetricsService,
                        @Value("${app.cache.product-ttl-minutes}") long ttlMinutes,
                        @Value("${app.instance-id}") String instanceId) {
                this.redisTemplate = redisTemplate;
                this.objectMapper = objectMapper;
                this.cacheMetricsService = cacheMetricsService;
                this.CACHE_TTL = Duration.ofMinutes(ttlMinutes);
                this.instanceId = instanceId;

        }

        public Optional<ProductResponse> get(Long productId) {

                String key = buildKey(productId);

                String cachedProduct = redisTemplate
                                .opsForValue()
                                .get(key);

                if (cachedProduct == null) {

                        cacheMetricsService.recordMiss();

                        log.info(
                                        "instance={} CACHE_MISS productId={}",
                                        instanceId,
                                        productId);

                        return Optional.empty();
                }

                try {

                        ProductResponse product = objectMapper.readValue(
                                        cachedProduct,
                                        ProductResponse.class);

                        Long ttl = redisTemplate.getExpire(key);

                        cacheMetricsService.recordHit();

                        log.info(
                                        "instance={} CACHE_HIT productId={} remainingTtlSeconds={}",
                                        instanceId,productId, ttl);

                        return Optional.of(product);

                } catch (JacksonException exception) {

                        log.error(
                                        "CACHE_DESERIALIZATION_FAILED productId={}",
                                        productId,
                                        exception);

                        throw new IllegalStateException(
                                        "Failed to read product from cache",
                                        exception);
                }
        }

        public void put(
                        Long productId,
                        ProductResponse product) {

                String key = buildKey(productId);

                try {

                        String json = objectMapper.writeValueAsString(product);

                        redisTemplate
                                        .opsForValue()
                                        .set(
                                                        key,
                                                        json,
                                                        CACHE_TTL);

                        cacheMetricsService.recordWrite();

                        log.info(
                                        "instance={} CACHE_WRITE productId={} ttlSeconds={}",
                                        instanceId,
                                        productId,
                                        CACHE_TTL.toSeconds());

                } catch (JacksonException exception) {

                        log.error(
                                        "CACHE_SERIALIZATION_FAILED productId={}",
                                        productId,
                                        exception);

                        throw new IllegalStateException(
                                        "Failed to write product to cache",
                                        exception);
                }
        }

        private String buildKey(Long productId) {

                return CACHE_PREFIX + productId;
        }

        public void evict(Long productId) {

                String key = buildKey(productId);

                Boolean deleted = redisTemplate.delete(key);

                if (Boolean.TRUE.equals(deleted)) {

                        cacheMetricsService.recordEviction();

                        log.info(
                                        "instance={} CACHE_EVICT productId={}",
                                        instanceId,
                                        productId);

                } else {

                        log.info(
                                        "CACHE_EVICT_SKIPPED productId={} reason=KEY_NOT_FOUND",
                                        productId);
                }
        }
}