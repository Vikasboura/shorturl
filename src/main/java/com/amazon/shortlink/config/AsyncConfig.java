package com.amazon.shortlink.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Thread Pool Configuration for asynchronous click analytics logging.
 * Decouples redirect latency from DynamoDB persistence.
 */
@Configuration
public class AsyncConfig {

    @Value("${shortlink.async.core-pool-size:4}")
    private int corePoolSize;

    @Value("${shortlink.async.max-pool-size:16}")
    private int maxPoolSize;

    @Value("${shortlink.async.queue-capacity:1000}")
    private int queueCapacity;

    @Bean(name = "analyticsExecutor")
    public Executor analyticsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("analytics-worker-");
        // CallerRunsPolicy guarantees resilient degradation under extreme bursts
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
