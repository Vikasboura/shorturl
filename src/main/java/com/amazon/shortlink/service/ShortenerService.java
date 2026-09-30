package com.amazon.shortlink.service;

import com.amazon.shortlink.domain.ShortUrl;
import com.amazon.shortlink.dto.CacheStatsResponse;
import com.amazon.shortlink.dto.ShortenRequest;
import com.amazon.shortlink.dto.ShortenResponse;
import com.amazon.shortlink.dto.UrlStatsResponse;

import java.util.List;

/**
 * Service contract interface for short link operations, resolution, and telemetry.
 * Adheres to Dependency Inversion Principle (DIP).
 */
public interface ShortenerService {

    ShortenResponse shortenUrl(ShortenRequest request);

    String resolveAndTrack(String code, String referrer, String userAgent, String clientIp, String country);

    UrlStatsResponse getStats(String code);

    List<ShortUrl> getRecentLinks(int limit);

    CacheStatsResponse getCacheStats();
}
