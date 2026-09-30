package com.amazon.shortlink.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomLruCacheTest {

    @Test
    @DisplayName("Should reject invalid capacity")
    void testInvalidCapacity() {
        assertThatThrownBy(() -> new CustomLruCache<String, String>(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should put and retrieve items successfully")
    void testBasicPutAndGet() {
        CustomLruCache<String, String> cache = new CustomLruCache<>(3);
        cache.put("k1", "v1");
        cache.put("k2", "v2");

        assertThat(cache.get("k1")).isEqualTo("v1");
        assertThat(cache.get("k2")).isEqualTo("v2");
        assertThat(cache.get("k3")).isNull();
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should evict least recently used item when capacity is exceeded")
    void testLruEviction() {
        CustomLruCache<String, String> cache = new CustomLruCache<>(3);
        cache.put("k1", "v1");
        cache.put("k2", "v2");
        cache.put("k3", "v3");

        // Now cache is full: [k3, k2, k1]. Adding k4 should evict k1 (LRU).
        cache.put("k4", "v4");

        assertThat(cache.get("k1")).isNull(); // Evicted
        assertThat(cache.get("k2")).isEqualTo("v2");
        assertThat(cache.get("k3")).isEqualTo("v3");
        assertThat(cache.get("k4")).isEqualTo("v4");
        assertThat(cache.getEvictionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Accessing an item should promote it to head, protecting it from eviction")
    void testPromotionOnGet() {
        CustomLruCache<String, String> cache = new CustomLruCache<>(3);
        cache.put("k1", "v1");
        cache.put("k2", "v2");
        cache.put("k3", "v3");

        // Access k1 -> promoted to head. List order is now: k1, k3, k2
        assertThat(cache.get("k1")).isEqualTo("v1");

        // Adding k4 should evict k2 (new LRU), NOT k1
        cache.put("k4", "v4");

        assertThat(cache.get("k1")).isEqualTo("v1"); // Preserved
        assertThat(cache.get("k2")).isNull();        // Evicted
        assertThat(cache.get("k3")).isEqualTo("v3");
        assertThat(cache.get("k4")).isEqualTo("v4");
    }

    @Test
    @DisplayName("Updating an existing key should update value and promote to head")
    void testUpdateExistingKey() {
        CustomLruCache<String, String> cache = new CustomLruCache<>(2);
        cache.put("k1", "v1");
        cache.put("k2", "v2");

        // Update k1
        cache.put("k1", "v1_updated");

        // Insert k3 -> should evict k2, keeping k1
        cache.put("k3", "v3");

        assertThat(cache.get("k1")).isEqualTo("v1_updated");
        assertThat(cache.get("k2")).isNull();
        assertThat(cache.get("k3")).isEqualTo("v3");
    }

    @Test
    @DisplayName("Should accurately record hit and miss telemetry")
    void testTelemetryMetrics() {
        CustomLruCache<String, String> cache = new CustomLruCache<>(5);
        cache.put("a", "1");
        cache.put("b", "2");

        // 3 hits
        cache.get("a");
        cache.get("a");
        cache.get("b");

        // 2 misses
        cache.get("c");
        cache.get("d");

        assertThat(cache.getHitCount()).isEqualTo(3);
        assertThat(cache.getMissCount()).isEqualTo(2);
        assertThat(cache.getHitRatio()).isEqualTo(3.0 / 5.0);
    }

    @Test
    @DisplayName("Should remove key and clear cache cleanly")
    void testRemoveAndClear() {
        CustomLruCache<String, String> cache = new CustomLruCache<>(5);
        cache.put("k1", "v1");
        cache.put("k2", "v2");

        assertThat(cache.remove("k1")).isTrue();
        assertThat(cache.get("k1")).isNull();
        assertThat(cache.size()).isEqualTo(1);

        cache.clear();
        assertThat(cache.size()).isZero();
        assertThat(cache.get("k2")).isNull();
    }

    @Test
    @DisplayName("Should maintain thread safety under concurrent operations")
    void testConcurrentAccess() throws InterruptedException {
        int threads = 8;
        int operationsPerThread = 500;
        CustomLruCache<Integer, String> cache = new CustomLruCache<>(50);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        int key = (threadId * 100) + (i % 20);
                        cache.put(key, "val-" + key);
                        cache.get(key);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(errors.get()).isZero();
        assertThat(cache.size()).isLessThanOrEqualTo(50);
    }
}
