package com.amazon.shortlink.algorithm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-memory Token Bucket Rate Limiter implemented from scratch.
 * 
 * Enforces per-client (e.g. client IP) rate limits and provides RFC 6585
 * compliant 'Retry-After' calculations when limits are breached.
 */
@Component
public class TokenBucketRateLimiter {

    public record RateLimitResult(boolean isAllowed, long remainingTokens, long retryAfterSeconds) {}

    private static class TokenBucket {
        private final double capacity;
        private final double refillRatePerSecond;
        private double availableTokens;
        private long lastRefillNanos;
        private volatile long lastAccessMillis;

        TokenBucket(double capacity, double refillRatePerSecond) {
            this.capacity = capacity;
            this.refillRatePerSecond = refillRatePerSecond;
            this.availableTokens = capacity;
            this.lastRefillNanos = System.nanoTime();
            this.lastAccessMillis = System.currentTimeMillis();
        }

        synchronized RateLimitResult tryConsume(double tokensRequested) {
            long nowNanos = System.nanoTime();
            lastAccessMillis = System.currentTimeMillis();

            // Refill tokens based on elapsed nanoseconds
            double elapsedSeconds = (nowNanos - lastRefillNanos) / 1_000_000_000.0;
            if (elapsedSeconds > 0) {
                availableTokens = Math.min(capacity, availableTokens + (elapsedSeconds * refillRatePerSecond));
                lastRefillNanos = nowNanos;
            }

            if (availableTokens >= tokensRequested) {
                availableTokens -= tokensRequested;
                return new RateLimitResult(true, (long) availableTokens, 0);
            }

            // Limit exceeded: calculate seconds until at least 1 token is available
            double tokensNeeded = tokensRequested - availableTokens;
            long retryAfterSeconds = Math.max(1, (long) Math.ceil(tokensNeeded / refillRatePerSecond));
            return new RateLimitResult(false, (long) availableTokens, retryAfterSeconds);
        }

        long getLastAccessMillis() {
            return lastAccessMillis;
        }
    }

    private final double defaultCapacity;
    private final double defaultRefillRatePerSecond;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(
            @Value("${shortlink.rate-limiter.capacity:60}") double defaultCapacity,
            @Value("${shortlink.rate-limiter.refill-rate-per-second:10.0}") double defaultRefillRatePerSecond) {
        this.defaultCapacity = defaultCapacity;
        this.defaultRefillRatePerSecond = defaultRefillRatePerSecond;
    }

    /**
     * Attempts to acquire a single token for the given client identifier (e.g. IP).
     *
     * @param clientId Client IP or token
     * @return RateLimitResult containing whether request is allowed, remaining tokens, and retry-after
     */
    public RateLimitResult tryAcquire(String clientId) {
        return tryAcquire(clientId, 1.0);
    }

    /**
     * Attempts to acquire specified number of tokens for the given client identifier.
     *
     * @param clientId Client IP or token
     * @param tokens   Tokens to consume
     * @return RateLimitResult
     */
    public RateLimitResult tryAcquire(String clientId, double tokens) {
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = "UNKNOWN_CLIENT";
        }
        TokenBucket bucket = buckets.computeIfAbsent(clientId,
                k -> new TokenBucket(defaultCapacity, defaultRefillRatePerSecond));
        return bucket.tryConsume(tokens);
    }

    /**
     * Cleans up idle buckets older than maxIdleMillis to prevent memory leaks from one-off IPs.
     *
     * @param maxIdleMillis Idle expiration threshold
     */
    public void cleanupIdleBuckets(long maxIdleMillis) {
        long threshold = System.currentTimeMillis() - maxIdleMillis;
        buckets.entrySet().removeIf(entry -> entry.getValue().getLastAccessMillis() < threshold);
    }

    public int getActiveClientCount() {
        return buckets.size();
    }

    public void clear() {
        buckets.clear();
    }
}
