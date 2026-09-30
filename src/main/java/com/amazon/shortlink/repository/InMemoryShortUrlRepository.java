package com.amazon.shortlink.repository;

import com.amazon.shortlink.domain.ShortUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance In-Memory implementation of ShortUrlRepository.
 * 
 * Emulates DynamoDB conditional write semantics (attribute_not_exists) using
 * concurrent putIfAbsent, allowing instant local testing without Docker or AWS.
 */
@Repository
@ConditionalOnProperty(name = "aws.dynamodb.mode", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryShortUrlRepository implements ShortUrlRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryShortUrlRepository.class);
    private final ConcurrentHashMap<String, ShortUrl> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<ShortUrl> findByShortCode(String shortCode) {
        if (shortCode == null || shortCode.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(storage.get(shortCode.trim()));
    }

    @Override
    public boolean saveWithCondition(ShortUrl shortUrl) {
        if (shortUrl == null || shortUrl.getShortCode() == null) {
            return false;
        }
        // Atomic putIfAbsent emulates DynamoDB attribute_not_exists(short_code)
        ShortUrl existing = storage.putIfAbsent(shortUrl.getShortCode(), shortUrl);
        if (existing != null) {
            log.warn("Collision detected in InMemory repository for shortCode: {}", shortUrl.getShortCode());
            return false;
        }
        return true;
    }

    @Override
    public void delete(String shortCode) {
        if (shortCode != null) {
            storage.remove(shortCode.trim());
        }
    }

    @Override
    public List<ShortUrl> findRecent(int limit) {
        return storage.values().stream()
                .sorted(Comparator.comparing(ShortUrl::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }
}
