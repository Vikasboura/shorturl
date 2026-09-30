package com.amazon.shortlink.config;

import com.amazon.shortlink.algorithm.CustomLruCache;
import com.amazon.shortlink.domain.ShortUrl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cache Configuration providing singleton CustomLruCache for hot URLs.
 */
@Configuration
public class CacheConfig {

    @Value("${shortlink.cache.max-size:10000}")
    private int cacheMaxSize;

    @Bean
    public CustomLruCache<String, ShortUrl> shortUrlCache() {
        return new CustomLruCache<>(cacheMaxSize);
    }
}
