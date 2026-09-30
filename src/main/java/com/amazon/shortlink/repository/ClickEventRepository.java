package com.amazon.shortlink.repository;

import com.amazon.shortlink.domain.ClickEvent;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.ArrayList;
import java.util.List;

/**
 * DynamoDB Repository for ClickEvent entities.
 * Supports primary table query by short_code as well as GSI DateIndex queries.
 */
@Repository
public class ClickEventRepository {

    private final DynamoDbTable<ClickEvent> clickEventTable;

    public ClickEventRepository(DynamoDbTable<ClickEvent> clickEventTable) {
        this.clickEventTable = clickEventTable;
    }

    /**
     * Persists an analytics click event.
     *
     * @param event The event to save
     */
    public void save(ClickEvent event) {
        if (event != null) {
            clickEventTable.putItem(event);
        }
    }

    /**
     * Finds recent click events for a specific short code.
     *
     * @param shortCode The short code partition key
     * @param limit     Max events to retrieve
     * @return List of ClickEvents
     */
    public List<ClickEvent> findByShortCode(String shortCode, int limit) {
        if (shortCode == null || shortCode.trim().isEmpty()) {
            return List.of();
        }

        Key key = Key.builder().partitionValue(shortCode.trim()).build();
        QueryConditional queryConditional = QueryConditional.keyEqualTo(key);

        List<ClickEvent> events = new ArrayList<>();
        for (Page<ClickEvent> page : clickEventTable.query(r -> r.queryConditional(queryConditional).limit(limit))) {
            events.addAll(page.items());
            if (events.size() >= limit) {
                break;
            }
        }
        return events.stream().limit(limit).toList();
    }

    /**
     * Counts the total number of click events recorded for a given short code.
     *
     * @param shortCode The short code
     * @return Total click count
     */
    public long countByShortCode(String shortCode) {
        if (shortCode == null || shortCode.trim().isEmpty()) {
            return 0;
        }

        Key key = Key.builder().partitionValue(shortCode.trim()).build();
        QueryConditional queryConditional = QueryConditional.keyEqualTo(key);

        long total = 0;
        for (Page<ClickEvent> page : clickEventTable.query(r -> r.queryConditional(queryConditional))) {
            total += page.items().size();
        }
        return total;
    }

    /**
     * Queries click events for a short code within a date range using the DateIndex GSI.
     *
     * @param shortCode Short code
     * @param startDate Start date string (YYYY-MM-DD)
     * @param endDate   End date string (YYYY-MM-DD)
     * @return List of ClickEvents
     */
    public List<ClickEvent> findByShortCodeAndDateRange(String shortCode, String startDate, String endDate) {
        DynamoDbIndex<ClickEvent> dateIndex = clickEventTable.index(ClickEvent.DATE_INDEX);

        Key startKey = Key.builder().partitionValue(shortCode).sortValue(startDate).build();
        Key endKey = Key.builder().partitionValue(shortCode).sortValue(endDate).build();

        QueryConditional query = QueryConditional.sortBetween(startKey, endKey);

        List<ClickEvent> results = new ArrayList<>();
        for (Page<ClickEvent> page : dateIndex.query(r -> r.queryConditional(query))) {
            results.addAll(page.items());
        }
        return results;
    }
}
