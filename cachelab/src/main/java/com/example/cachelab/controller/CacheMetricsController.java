package com.example.cachelab.controller;

import com.example.cachelab.service.CacheMetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/cache")
public class CacheMetricsController {

    private final CacheMetricsService cacheMetricsService;

    public CacheMetricsController(
            CacheMetricsService cacheMetricsService) {
        this.cacheMetricsService = cacheMetricsService;
    }

    @GetMapping("/metrics")
    public Map<String, Object> getMetrics() {

        return Map.of(
                "hits",
                cacheMetricsService.getHits(),

                "misses",
                cacheMetricsService.getMisses(),

                "writes",
                cacheMetricsService.getWrites(),

                "evictions",
                cacheMetricsService.getEvictions(),

                "hitRatio",
                cacheMetricsService.getHitRatio());
    }
}