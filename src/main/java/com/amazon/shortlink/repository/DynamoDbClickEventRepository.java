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
 * AWS DynamoDB implementation of ClickEventRepository.
 */
@Repository
public class DynamoDbClickEventRepository implements ClickEventRepository {

    private final DynamoDbTable<ClickEvent> clickEventTable;

    public DynamoDbClickEventRepository(DynamoDbTable<ClickEvent> clickEventTable) {
        this.clickEventTable = clickEventTable;
    }

    @Override
    public void save(ClickEvent event) {
        if (event != null) {
            clickEventTable.putItem(event);
        }
    }

    @Override
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

    @Override
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

    @Override
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
