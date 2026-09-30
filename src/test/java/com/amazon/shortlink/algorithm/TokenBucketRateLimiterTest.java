package com.amazon.shortlink.algorithm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketRateLimiterTest {

    private TokenBucketRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        // Capacity of 5 tokens, refilling at 2 tokens per second
        rateLimiter = new TokenBucketRateLimiter(5.0, 2.0);
    }

    @Test
    @DisplayName("Should allow requests up to burst capacity")
    void testBurstCapacity() {
        String clientIp = "192.168.1.100";

        for (int i = 0; i < 5; i++) {
            TokenBucketRateLimiter.RateLimitResult result = rateLimiter.tryAcquire(clientIp);
            assertThat(result.isAllowed()).as("Request %d should be allowed", i + 1).isTrue();
        }

        // 6th request should exceed capacity
        TokenBucketRateLimiter.RateLimitResult sixth = rateLimiter.tryAcquire(clientIp);
        assertThat(sixth.isAllowed()).isFalse();
        assertThat(sixth.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should isolate rate limits between different clients")
    void testClientIsolation() {
        String clientA = "10.0.0.1";
        String clientB = "10.0.0.2";

        // Exhaust client A's bucket
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire(clientA);
        }
        assertThat(rateLimiter.tryAcquire(clientA).isAllowed()).isFalse();

        // Client B must still have full capacity
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.tryAcquire(clientB).isAllowed()).isTrue();
        }
    }

    @Test
    @DisplayName("Should replenish tokens over time")
    void testTokenRefill() throws InterruptedException {
        String clientIp = "172.16.0.1";

        // Consume all 5 tokens
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire(clientIp);
        }
        assertThat(rateLimiter.tryAcquire(clientIp).isAllowed()).isFalse();

        // Wait 600ms (refill rate is 2/sec -> 1.2 tokens refilled)
        Thread.sleep(600);

        TokenBucketRateLimiter.RateLimitResult afterWait = rateLimiter.tryAcquire(clientIp);
        assertThat(afterWait.isAllowed()).isTrue();
    }

    @Test
    @DisplayName("Should enforce rate limit accurately under concurrent thread pressure")
    void testConcurrentRateLimiting() throws InterruptedException {
        int threads = 10;
        int requestsPerThread = 5;
        String clientIp = "192.168.1.50";

        // Capacity = 10, refill = 0.1/sec (virtually zero during the burst)
        TokenBucketRateLimiter strictLimiter = new TokenBucketRateLimiter(10.0, 0.1);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < requestsPerThread; i++) {
                        TokenBucketRateLimiter.RateLimitResult res = strictLimiter.tryAcquire(clientIp);
                        if (res.isAllowed()) {
                            allowedCount.incrementAndGet();
                        } else {
                            rejectedCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Total allowed requests must exactly equal the initial burst capacity (10)
        assertThat(allowedCount.get()).isEqualTo(10);
        assertThat(rejectedCount.get()).isEqualTo((threads * requestsPerThread) - 10);
    }
}
