package com.example.cachelab.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class CacheMetricsService {

    private final AtomicLong hits = new AtomicLong();

    private final AtomicLong misses = new AtomicLong();

    private final AtomicLong writes = new AtomicLong();

    private final AtomicLong evictions = new AtomicLong();

    public void recordHit() {
        hits.incrementAndGet();
    }

    public void recordMiss() {
        misses.incrementAndGet();
    }

    public void recordWrite() {
        writes.incrementAndGet();
    }

    public long getHits() {
        return hits.get();
    }

    public long getMisses() {
        return misses.get();
    }

    public long getWrites() {
        return writes.get();
    }

    public double getHitRatio() {

        long total = hits.get() + misses.get();

        if (total == 0) {
            return 0;
        }

        return (double) hits.get() / total;
    }

    public void recordEviction() {
        evictions.incrementAndGet();
    }

    public long getEvictions() {
        return evictions.get();
    }
}