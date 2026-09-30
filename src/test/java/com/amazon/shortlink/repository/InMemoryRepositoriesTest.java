package com.amazon.shortlink.repository;

import com.amazon.shortlink.domain.ClickEvent;
import com.amazon.shortlink.domain.ShortUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRepositoriesTest {

    private InMemoryShortUrlRepository urlRepository;
    private InMemoryClickEventRepository clickRepository;

    @BeforeEach
    void setUp() {
        urlRepository = new InMemoryShortUrlRepository();
        clickRepository = new InMemoryClickEventRepository();
    }

    @Test
    @DisplayName("InMemoryShortUrlRepository should save with condition and reject collisions")
    void testSaveWithCondition() {
        ShortUrl url1 = new ShortUrl("k9Z2a1x", "https://aws.amazon.com", System.currentTimeMillis(), null, false, null);
        ShortUrl url2 = new ShortUrl("k9Z2a1x", "https://another.com", System.currentTimeMillis(), null, false, null);

        boolean first = urlRepository.saveWithCondition(url1);
        boolean second = urlRepository.saveWithCondition(url2);

        assertThat(first).isTrue();
        assertThat(second).isFalse(); // Collided!
    }

    @Test
    @DisplayName("InMemoryShortUrlRepository should find, delete, and list recent links")
    void testFindDeleteAndRecent() {
        ShortUrl url1 = new ShortUrl("link1", "https://aws.amazon.com", 1000L, null, false, null);
        ShortUrl url2 = new ShortUrl("link2", "https://amazon.com", 2000L, null, false, null);

        urlRepository.saveWithCondition(url1);
        urlRepository.saveWithCondition(url2);

        Optional<ShortUrl> found = urlRepository.findByShortCode("link1");
        assertThat(found).isPresent();
        assertThat(found.get().getLongUrl()).isEqualTo("https://aws.amazon.com");

        List<ShortUrl> recent = urlRepository.findRecent(10);
        assertThat(recent).hasSize(2);
        // Descending order by createdAt
        assertThat(recent.get(0).getShortCode()).isEqualTo("link2");

        urlRepository.delete("link1");
        assertThat(urlRepository.findByShortCode("link1")).isEmpty();
    }

    @Test
    @DisplayName("InMemoryClickEventRepository should save, count, and filter by date range")
    void testClickEventRepository() {
        ClickEvent c1 = new ClickEvent("testCode", "id1", "2026-10-01", "DIRECT", "curl", "curl", "Linux", "h1", "US");
        ClickEvent c2 = new ClickEvent("testCode", "id2", "2026-10-02", "google.com", "Chrome", "Chrome", "Windows", "h2", "US");
        ClickEvent c3 = new ClickEvent("otherCode", "id3", "2026-10-01", "DIRECT", "curl", "curl", "Linux", "h3", "US");

        clickRepository.save(c1);
        clickRepository.save(c2);
        clickRepository.save(c3);

        assertThat(clickRepository.countByShortCode("testCode")).isEqualTo(2);
        assertThat(clickRepository.findByShortCode("testCode", 10)).hasSize(2);

        List<ClickEvent> filtered = clickRepository.findByShortCodeAndDateRange("testCode", "2026-10-01", "2026-10-01");
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).getTimestampEventId()).isEqualTo("id1");
    }
}
