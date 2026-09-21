package com.example.cachelab.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.example.cachelab.dto.ProductResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class ProductCacheService {

    private static final Logger log =
            LoggerFactory.getLogger(ProductCacheService.class);

    private static final String CACHE_PREFIX = "product:";

    private static final Duration CACHE_TTL =
            Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;


    public ProductCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }


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

        } catch (JacksonException exception) {

            log.error(
                    "CACHE_DESERIALIZATION_FAILED productId={}",
                    productId,
                    exception
            );

            throw new IllegalStateException(
                    "Failed to read product from cache",
                    exception
            );
        }
    }


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
                            CACHE_TTL
                    );

            log.info(
                    "CACHE_WRITE productId={} ttlSeconds={}",
                    productId,
                    CACHE_TTL.toSeconds()
            );

        } catch (JacksonException exception) {

            log.error(
                    "CACHE_SERIALIZATION_FAILED productId={}",
                    productId,
                    exception
            );

            throw new IllegalStateException(
                    "Failed to write product to cache",
                    exception
            );
        }
    }


    private String buildKey(Long productId) {

        return CACHE_PREFIX + productId;
    }
}