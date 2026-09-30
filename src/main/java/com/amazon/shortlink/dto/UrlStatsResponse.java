package com.amazon.shortlink.dto;

import java.util.Map;

public record UrlStatsResponse(
        String shortCode,
        String longUrl,
        long totalClicks,
        Map<String, Long> clicksPerDay,
        Map<String, Long> topReferrers,
        Map<String, Long> browserDistribution,
        Map<String, Long> osDistribution,
        Long createdAt,
        Long expiresAt
) {}
