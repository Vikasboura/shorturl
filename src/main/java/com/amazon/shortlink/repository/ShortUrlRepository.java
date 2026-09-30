package com.amazon.shortlink.repository;

import com.amazon.shortlink.domain.ShortUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DynamoDB Repository for ShortUrl entities.
 * Enforces conditional write semantics to avoid short code overwrites.
 */
@Repository
public class ShortUrlRepository {

    private static final Logger log = LoggerFactory.getLogger(ShortUrlRepository.class);

    private final DynamoDbTable<ShortUrl> shortUrlTable;

    public ShortUrlRepository(DynamoDbTable<ShortUrl> shortUrlTable) {
        this.shortUrlTable = shortUrlTable;
    }

    /**
     * Retrieves a ShortUrl entity by its short code partition key.
     *
     * @param shortCode The short code to look up
     * @return Optional containing the ShortUrl, or empty if not found
     */
    public Optional<ShortUrl> findByShortCode(String shortCode) {
        if (shortCode == null || shortCode.trim().isEmpty()) {
            return Optional.empty();
        }

        Key key = Key.builder().partitionValue(shortCode.trim()).build();
        ShortUrl item = shortUrlTable.getItem(r -> r.key(key));
        return Optional.ofNullable(item);
    }

    /**
     * Atomically saves a ShortUrl only if the short_code does NOT already exist in DynamoDB.
     *
     * @param shortUrl The entity to persist
     * @return true if successfully saved; false if a collision occurred (ConditionalCheckFailedException)
     */
    public boolean saveWithCondition(ShortUrl shortUrl) {
        Expression condition = Expression.builder()
                .expression("attribute_not_exists(short_code)")
                .build();

        PutItemEnhancedRequest<ShortUrl> request = PutItemEnhancedRequest.builder(ShortUrl.class)
                .item(shortUrl)
                .conditionExpression(condition)
                .build();

        try {
            shortUrlTable.putItem(request);
            return true;
        } catch (ConditionalCheckFailedException e) {
            log.warn("Collision detected in DynamoDB for shortCode: {}", shortUrl.getShortCode());
            return false;
        }
    }

    /**
     * Deletes a short link by code.
     *
     * @param shortCode Short code to delete
     */
    public void delete(String shortCode) {
        Key key = Key.builder().partitionValue(shortCode).build();
        shortUrlTable.deleteItem(r -> r.key(key));
    }

    /**
     * Retrieves recent links up to the specified limit (used for dashboard view).
     *
     * @param limit Max items to return
     * @return List of ShortUrl items
     */
    public List<ShortUrl> findRecent(int limit) {
        List<ShortUrl> result = new ArrayList<>();
        for (Page<ShortUrl> page : shortUrlTable.scan(r -> r.limit(limit))) {
            result.addAll(page.items());
            if (result.size() >= limit) {
                break;
            }
        }
        return result.stream().limit(limit).toList();
    }
}
