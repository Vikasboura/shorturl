package com.amazon.shortlink.service;

import com.amazon.shortlink.algorithm.CodeGenerator;
import com.amazon.shortlink.algorithm.CustomLruCache;
import com.amazon.shortlink.algorithm.ShortCodeGenerator;
import com.amazon.shortlink.domain.ShortUrl;
import com.amazon.shortlink.dto.CacheStatsResponse;
import com.amazon.shortlink.dto.ShortenRequest;
import com.amazon.shortlink.dto.ShortenResponse;
import com.amazon.shortlink.dto.UrlStatsResponse;
import com.amazon.shortlink.exception.AliasAlreadyExistsException;
import com.amazon.shortlink.exception.InvalidUrlException;
import com.amazon.shortlink.exception.UrlExpiredException;
import com.amazon.shortlink.exception.UrlNotFoundException;
import com.amazon.shortlink.repository.ShortUrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Core business service for short link lifecycle management, resolution,
 * collision retry loop, and cache coordination.
 */
@Service
public class UrlShortenerService {

    private static final Logger log = LoggerFactory.getLogger(UrlShortenerService.class);
    private static final int MAX_COLLISION_RETRIES = 5;

    private final ShortUrlRepository shortUrlRepository;
    private final CodeGenerator shortCodeGenerator;
    private final AnalyticsService analyticsService;
    private final CustomLruCache<String, ShortUrl> shortUrlCache;

    @Value("${shortlink.base-url:http://localhost:8080}")
    private String baseUrl;

    public UrlShortenerService(ShortUrlRepository shortUrlRepository,
                               CodeGenerator shortCodeGenerator,
                               AnalyticsService analyticsService,
                               CustomLruCache<String, ShortUrl> shortUrlCache) {
        this.shortUrlRepository = shortUrlRepository;
        this.shortCodeGenerator = shortCodeGenerator;
        this.analyticsService = analyticsService;
        this.shortUrlCache = shortUrlCache;
    }

    /**
     * Shortens a target URL with optional vanity alias and TTL expiration.
     */
    public ShortenResponse shortenUrl(ShortenRequest request) {
        validateUrl(request.url());

        Long expiresAt = null;
        if (request.ttlSeconds() != null && request.ttlSeconds() > 0) {
            expiresAt = Instant.now().getEpochSecond() + request.ttlSeconds();
        }

        long nowMillis = Instant.now().toEpochMilli();
        boolean isCustom = request.customAlias() != null && !request.customAlias().isBlank();

        if (isCustom) {
            String alias = request.customAlias().trim();
            if (!shortCodeGenerator.isValidCustomAlias(alias)) {
                throw new InvalidUrlException("Custom alias '" + alias + "' is invalid or reserved.");
            }

            ShortUrl shortUrl = new ShortUrl(alias, request.url().trim(), nowMillis, expiresAt, true, request.creatorId());
            boolean saved = shortUrlRepository.saveWithCondition(shortUrl);
            if (!saved) {
                throw new AliasAlreadyExistsException(alias);
            }

            shortUrlCache.put(alias, shortUrl);
            return toResponse(shortUrl);
        }

        // Automatic random Base62 generation with collision retry loop
        for (int attempt = 1; attempt <= MAX_COLLISION_RETRIES; attempt++) {
            String generatedCode = shortCodeGenerator.generate();
            ShortUrl shortUrl = new ShortUrl(generatedCode, request.url().trim(), nowMillis, expiresAt, false, request.creatorId());

            boolean saved = shortUrlRepository.saveWithCondition(shortUrl);
            if (saved) {
                shortUrlCache.put(generatedCode, shortUrl);
                return toResponse(shortUrl);
            }
            log.warn("Collision encountered on attempt {} for code {}. Retrying...", attempt, generatedCode);
        }

        throw new IllegalStateException("Exceeded maximum collision retry attempts (" + MAX_COLLISION_RETRIES + ").");
    }

    /**
     * Resolves short code to destination URL with cache check and async click tracking.
     */
    public String resolveAndTrack(String code, String referrer, String userAgent, String clientIp, String country) {
        if (code == null || code.isBlank()) {
            throw new UrlNotFoundException("EMPTY_CODE");
        }
        String cleanCode = code.trim();

        // 1. Check in-memory LRU Cache (p99 < 1ms)
        ShortUrl shortUrl = shortUrlCache.get(cleanCode);

        // 2. Fall back to DynamoDB on cache miss
        if (shortUrl == null) {
            shortUrl = shortUrlRepository.findByShortCode(cleanCode)
                    .orElseThrow(() -> new UrlNotFoundException(cleanCode));
            
            if (shortUrl.isExpired()) {
                throw new UrlExpiredException(cleanCode);
            }
            // Populate cache
            shortUrlCache.put(cleanCode, shortUrl);
        } else {
            // Verify cached item has not expired
            if (shortUrl.isExpired()) {
                shortUrlCache.remove(cleanCode);
                throw new UrlExpiredException(cleanCode);
            }
        }

        // 3. Fire-and-forget asynchronous click analytics
        analyticsService.recordClickAsync(cleanCode, referrer, userAgent, clientIp, country);

        return shortUrl.getLongUrl();
    }

    /**
     * Retrieves analytics statistics for a given short code.
     */
    public UrlStatsResponse getStats(String code) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(code.trim())
                .orElseThrow(() -> new UrlNotFoundException(code));
        return analyticsService.getAggregatedStats(shortUrl);
    }

    /**
     * Retrieves recent shortened links for dashboard view.
     */
    public List<ShortUrl> getRecentLinks(int limit) {
        return shortUrlRepository.findRecent(limit);
    }

    /**
     * Returns cache hit/miss/eviction metrics.
     */
    public CacheStatsResponse getCacheStats() {
        return new CacheStatsResponse(
                shortUrlCache.getHitCount(),
                shortUrlCache.getMissCount(),
                shortUrlCache.getEvictionCount(),
                shortUrlCache.getHitRatio(),
                shortUrlCache.size(),
                shortUrlCache.getCapacity()
        );
    }

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidUrlException("Target URL cannot be empty.");
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new InvalidUrlException("URL scheme must be http or https.");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new InvalidUrlException("URL must contain a valid host domain.");
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidUrlException("Invalid URL format: " + e.getMessage());
        }
    }

    private ShortenResponse toResponse(ShortUrl shortUrl) {
        String cleanBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String fullShortUrl = cleanBase + "/" + shortUrl.getShortCode();
        return new ShortenResponse(
                shortUrl.getShortCode(),
                fullShortUrl,
                shortUrl.getLongUrl(),
                shortUrl.getCreatedAt(),
                shortUrl.getExpiresAt(),
                Boolean.TRUE.equals(shortUrl.getCustomAlias())
        );
    }
}
