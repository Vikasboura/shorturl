package com.amazon.shortlink.service;

import com.amazon.shortlink.domain.ShortUrl;
import com.amazon.shortlink.dto.UrlStatsResponse;

/**
 * Interface contract for analytics recording and aggregation.
 */
public interface AnalyticsService {

    void recordClickAsync(String shortCode, String referrer, String userAgent, String clientIp, String country);

    UrlStatsResponse getAggregatedStats(ShortUrl shortUrl);
}
