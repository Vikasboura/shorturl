package com.amazon.shortlink.repository;

import com.amazon.shortlink.domain.ClickEvent;

import java.util.List;

/**
 * Repository interface for ClickEvent analytics persistence operations.
 */
public interface ClickEventRepository {

    void save(ClickEvent event);

    List<ClickEvent> findByShortCode(String shortCode, int limit);

    long countByShortCode(String shortCode);

    List<ClickEvent> findByShortCodeAndDateRange(String shortCode, String startDate, String endDate);
}
