package com.amazon.shortlink.repository;

import com.amazon.shortlink.domain.ShortUrl;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for ShortUrl persistence operations.
 * Decouples storage implementation from domain business logic.
 */
public interface ShortUrlRepository {

    Optional<ShortUrl> findByShortCode(String shortCode);

    boolean saveWithCondition(ShortUrl shortUrl);

    void delete(String shortCode);

    List<ShortUrl> findRecent(int limit);
}
