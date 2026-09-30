package com.amazon.shortlink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Amazon ShortLink Service
 * Production-grade URL shortener with rate limiting, LRU caching, and analytics.
 */
@SpringBootApplication
@EnableAsync
public class ShortlinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShortlinkApplication.class, args);
    }
}
