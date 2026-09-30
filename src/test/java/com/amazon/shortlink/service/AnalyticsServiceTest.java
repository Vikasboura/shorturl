package com.amazon.shortlink.service;

import com.amazon.shortlink.domain.ClickEvent;
import com.amazon.shortlink.domain.ShortUrl;
import com.amazon.shortlink.dto.UrlStatsResponse;
import com.amazon.shortlink.repository.ClickEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private ClickEventRepository clickEventRepository;

    private DefaultAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new DefaultAnalyticsService(clickEventRepository);
    }

    @Test
    @DisplayName("Should parse browsers accurately from User-Agent strings")
    void testParseBrowser() {
        assertThat(DefaultAnalyticsService.parseBrowser("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")).isEqualTo("Chrome");
        assertThat(DefaultAnalyticsService.parseBrowser("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0")).isEqualTo("Edge");
        assertThat(DefaultAnalyticsService.parseBrowser("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15")).isEqualTo("Safari");
        assertThat(DefaultAnalyticsService.parseBrowser("Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:125.0) Gecko/20100101 Firefox/125.0")).isEqualTo("Firefox");
        assertThat(DefaultAnalyticsService.parseBrowser("curl/8.4.0")).isEqualTo("curl");
        assertThat(DefaultAnalyticsService.parseBrowser("k6-load-tester/1.0")).isEqualTo("k6-load-tester");
        assertThat(DefaultAnalyticsService.parseBrowser(null)).isEqualTo("Direct/Unknown");
    }

    @Test
    @DisplayName("Should parse OS accurately from User-Agent strings")
    void testParseOs() {
        assertThat(DefaultAnalyticsService.parseOs("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")).isEqualTo("Windows");
        assertThat(DefaultAnalyticsService.parseOs("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)")).isEqualTo("macOS");
        assertThat(DefaultAnalyticsService.parseOs("Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X)")).isEqualTo("iOS");
        assertThat(DefaultAnalyticsService.parseOs("Mozilla/5.0 (Linux; Android 14; Pixel 8)")).isEqualTo("Android");
        assertThat(DefaultAnalyticsService.parseOs("Mozilla/5.0 (X11; Linux x86_64)")).isEqualTo("Linux");
        assertThat(DefaultAnalyticsService.parseOs(null)).isEqualTo("Unknown");
    }

    @Test
    @DisplayName("Should anonymize IP address using SHA-256 salted hash")
    void testHashIp() {
        String hash1 = DefaultAnalyticsService.hashIp("203.0.113.195");
        String hash2 = DefaultAnalyticsService.hashIp("203.0.113.195");
        String hash3 = DefaultAnalyticsService.hashIp("198.51.100.1");

        assertThat(hash1).isNotEmpty().hasSize(16);
        assertThat(hash1).isEqualTo(hash2); // Deterministic
        assertThat(hash1).isNotEqualTo(hash3); // Unique
        assertThat(DefaultAnalyticsService.hashIp(null)).isEqualTo("ANONYMOUS");
    }

    @Test
    @DisplayName("Should persist ClickEvent with sanitized attributes on recordClickAsync")
    void testRecordClickAsync() {
        analyticsService.recordClickAsync("k9Z2a1x", "https://amazon.com", "curl/8.0", "192.168.1.1", "US");

        ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(clickEventRepository, times(1)).save(captor.capture());

        ClickEvent saved = captor.getValue();
        assertThat(saved.getShortCode()).isEqualTo("k9Z2a1x");
        assertThat(saved.getReferrer()).isEqualTo("https://amazon.com");
        assertThat(saved.getBrowser()).isEqualTo("curl");
        assertThat(saved.getCountry()).isEqualTo("US");
        assertThat(saved.getIpHash()).isNotEqualTo("192.168.1.1"); // IP hashed
    }

    @Test
    @DisplayName("Should aggregate click analytics correctly")
    void testGetAggregatedStats() {
        ShortUrl url = new ShortUrl("stats1", "https://aws.amazon.com", System.currentTimeMillis(), null, false, null);

        ClickEvent e1 = new ClickEvent("stats1", "2026-10-01T10:00:00#1", "2026-10-01", "DIRECT", "curl", "curl", "Linux", "hash1", "US");
        ClickEvent e2 = new ClickEvent("stats1", "2026-10-01T11:00:00#2", "2026-10-01", "google.com", "Chrome", "Chrome", "Windows", "hash2", "US");
        ClickEvent e3 = new ClickEvent("stats1", "2026-10-02T09:00:00#3", "2026-10-02", "DIRECT", "Chrome", "Chrome", "Windows", "hash3", "US");

        when(clickEventRepository.findByShortCode("stats1", 1000)).thenReturn(List.of(e1, e2, e3));

        UrlStatsResponse stats = analyticsService.getAggregatedStats(url);

        assertThat(stats.totalClicks()).isEqualTo(3);
        assertThat(stats.clicksPerDay()).containsEntry("2026-10-01", 2L);
        assertThat(stats.clicksPerDay()).containsEntry("2026-10-02", 1L);
        assertThat(stats.topReferrers()).containsEntry("DIRECT", 2L);
        assertThat(stats.topReferrers()).containsEntry("google.com", 1L);
        assertThat(stats.browserDistribution()).containsEntry("Chrome", 2L);
        assertThat(stats.osDistribution()).containsEntry("Windows", 2L);
    }
}
