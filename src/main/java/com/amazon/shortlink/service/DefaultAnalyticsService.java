package com.amazon.shortlink.service;

import com.amazon.shortlink.domain.ClickEvent;
import com.amazon.shortlink.domain.ShortUrl;
import com.amazon.shortlink.dto.UrlStatsResponse;
import com.amazon.shortlink.repository.ClickEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of AnalyticsService.
 * Records clicks asynchronously and aggregates statistics for dashboards.
 */
@Service
public class DefaultAnalyticsService implements AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAnalyticsService.class);
    private static final String IP_SALT = "AmazonShortLinkSalt2026";

    private final ClickEventRepository clickEventRepository;

    public DefaultAnalyticsService(ClickEventRepository clickEventRepository) {
        this.clickEventRepository = clickEventRepository;
    }

    @Override
    @Async("analyticsExecutor")
    public void recordClickAsync(String shortCode, String referrer, String userAgent, String clientIp, String country) {
        try {
            Instant now = Instant.now();
            String dateStr = LocalDate.ofInstant(now, ZoneOffset.UTC).toString();
            String timestampEventId = now.toString() + "#" + UUID.randomUUID().toString().substring(0, 8);

            String anonymizedIp = hashIp(clientIp);
            String browser = parseBrowser(userAgent);
            String os = parseOs(userAgent);

            ClickEvent event = new ClickEvent(
                    shortCode,
                    timestampEventId,
                    dateStr,
                    referrer != null && !referrer.isBlank() ? referrer : "DIRECT",
                    userAgent,
                    browser,
                    os,
                    anonymizedIp,
                    country != null && !country.isBlank() ? country : "UNKNOWN"
            );

            clickEventRepository.save(event);
            log.debug("Asynchronously recorded click for code: {}", shortCode);
        } catch (Exception e) {
            log.error("Failed to record async click event for code {}: {}", shortCode, e.getMessage(), e);
        }
    }

    @Override
    public UrlStatsResponse getAggregatedStats(ShortUrl shortUrl) {
        String code = shortUrl.getShortCode();
        List<ClickEvent> events = clickEventRepository.findByShortCode(code, 1000);
        long totalClicks = events.size();

        Map<String, Long> clicksPerDay = events.stream()
                .collect(Collectors.groupingBy(ClickEvent::getDateStr, TreeMap::new, Collectors.counting()));

        Map<String, Long> topReferrers = events.stream()
                .collect(Collectors.groupingBy(ClickEvent::getReferrer, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        Map<String, Long> browserDist = events.stream()
                .collect(Collectors.groupingBy(ClickEvent::getBrowser, Collectors.counting()));

        Map<String, Long> osDist = events.stream()
                .collect(Collectors.groupingBy(ClickEvent::getOs, Collectors.counting()));

        return new UrlStatsResponse(
                shortUrl.getShortCode(),
                shortUrl.getLongUrl(),
                totalClicks,
                clicksPerDay,
                topReferrers,
                browserDist,
                osDist,
                shortUrl.getCreatedAt(),
                shortUrl.getExpiresAt()
        );
    }

    public static String hashIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "ANONYMOUS";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((ip + IP_SALT).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "HASH_ERROR";
        }
    }

    public static String parseBrowser(String ua) {
        if (ua == null || ua.isBlank()) return "Direct/Unknown";
        String lower = ua.toLowerCase();
        if (lower.contains("edg/")) return "Edge";
        if (lower.contains("chrome/") && !lower.contains("edg/")) return "Chrome";
        if (lower.contains("safari/") && !lower.contains("chrome/")) return "Safari";
        if (lower.contains("firefox/")) return "Firefox";
        if (lower.contains("postman")) return "Postman";
        if (lower.contains("curl")) return "curl";
        if (lower.contains("k6")) return "k6-load-tester";
        return "Other";
    }

    public static String parseOs(String ua) {
        if (ua == null || ua.isBlank()) return "Unknown";
        String lower = ua.toLowerCase();
        if (lower.contains("windows")) return "Windows";
        if (lower.contains("iphone") || lower.contains("ipad")) return "iOS";
        if (lower.contains("mac os") || lower.contains("macintosh")) return "macOS";
        if (lower.contains("android")) return "Android";
        if (lower.contains("linux")) return "Linux";
        return "Other";
    }
}
