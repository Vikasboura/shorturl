package com.amazon.shortlink.repository;

import com.amazon.shortlink.domain.ClickEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory implementation of ClickEventRepository.
 * Stores events in a thread-safe concurrent collection for local analytics without Docker.
 */
@Repository
@ConditionalOnProperty(name = "aws.dynamodb.mode", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryClickEventRepository implements ClickEventRepository {

    private final ConcurrentLinkedDeque<ClickEvent> events = new ConcurrentLinkedDeque<>();

    @Override
    public void save(ClickEvent event) {
        if (event != null) {
            events.addFirst(event);
        }
    }

    @Override
    public List<ClickEvent> findByShortCode(String shortCode, int limit) {
        if (shortCode == null) return List.of();
        return events.stream()
                .filter(e -> shortCode.equals(e.getShortCode()))
                .limit(limit)
                .toList();
    }

    @Override
    public long countByShortCode(String shortCode) {
        if (shortCode == null) return 0;
        return events.stream()
                .filter(e -> shortCode.equals(e.getShortCode()))
                .count();
    }

    @Override
    public List<ClickEvent> findByShortCodeAndDateRange(String shortCode, String startDate, String endDate) {
        if (shortCode == null) return List.of();
        return events.stream()
                .filter(e -> shortCode.equals(e.getShortCode()))
                .filter(e -> e.getDateStr() != null &&
                        e.getDateStr().compareTo(startDate) >= 0 &&
                        e.getDateStr().compareTo(endDate) <= 0)
                .toList();
    }
}
