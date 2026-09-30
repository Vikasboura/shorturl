package com.amazon.shortlink.controller;

import com.amazon.shortlink.domain.ShortUrl;
import com.amazon.shortlink.dto.CacheStatsResponse;
import com.amazon.shortlink.dto.ShortenRequest;
import com.amazon.shortlink.dto.ShortenResponse;
import com.amazon.shortlink.dto.UrlStatsResponse;
import com.amazon.shortlink.service.ShortenerService;
import com.amazon.shortlink.service.UrlShortenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * REST Controller providing URL shortening, HTTP 302 redirects,
 * link stats, and cache telemetry.
 */
@RestController
@CrossOrigin(origins = "*")
@Tag(name = "ShortLink Operations", description = "Amazon Internal URL Shortener API")
public class UrlShortenerController {

    private final ShortenerService shortenerService;

    public UrlShortenerController(ShortenerService shortenerService) {
        this.shortenerService = shortenerService;
    }

    @PostMapping("/shorten")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create short link", description = "Generates a 7-character Base62 code or registers a custom alias.")
    @ApiResponse(responseCode = "201", description = "Short link created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid URL format or reserved alias")
    @ApiResponse(responseCode = "409", description = "Custom alias is already in use")
    public ShortenResponse shorten(@Valid @RequestBody ShortenRequest request) {
        return shortenerService.shortenUrl(request);
    }

    @GetMapping("/{code:[a-zA-Z0-9_-]+}")
    @Operation(summary = "Redirect to target URL", description = "Returns HTTP 302 Found with Location header and tracks click asynchronously.")
    @ApiResponse(responseCode = "302", description = "Redirect to destination URL")
    @ApiResponse(responseCode = "404", description = "Short link not found")
    @ApiResponse(responseCode = "410", description = "Short link has expired (TTL)")
    public ResponseEntity<Void> redirect(
            @PathVariable("code") String code,
            @RequestHeader(value = "Referer", required = false) String referrer,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "CloudFront-Viewer-Country", required = false) String country,
            HttpServletRequest request) {

        String clientIp = RateLimitingFilter.extractClientIp(request);
        String destinationUrl = shortenerService.resolveAndTrack(code, referrer, userAgent, clientIp, country);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(destinationUrl))
                .header("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate")
                .build();
    }

    @GetMapping("/stats/{code:[a-zA-Z0-9_-]+}")
    @Operation(summary = "Get link analytics", description = "Aggregates click counts, daily trends, top referrers, and browser distribution.")
    public UrlStatsResponse getStats(@PathVariable("code") String code) {
        return shortenerService.getStats(code);
    }

    @GetMapping("/api/links/recent")
    @Operation(summary = "List recent short links", description = "Returns latest shortened links for administrative dashboard view.")
    public List<ShortUrl> getRecentLinks(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        return shortenerService.getRecentLinks(limit);
    }

    @GetMapping("/api/cache/metrics")
    @Operation(summary = "Get LRU cache metrics", description = "Telemetry on hit count, miss count, eviction count, and hit ratio.")
    public CacheStatsResponse getCacheMetrics() {
        return shortenerService.getCacheStats();
    }
}
