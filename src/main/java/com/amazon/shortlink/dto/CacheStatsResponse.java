package com.amazon.shortlink.dto;

public record CacheStatsResponse(
        long hitCount,
        long missCount,
        long evictionCount,
        double hitRatio,
        int currentSize,
        int capacity
) {}
