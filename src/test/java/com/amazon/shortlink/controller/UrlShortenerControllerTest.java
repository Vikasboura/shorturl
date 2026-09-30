package com.amazon.shortlink.controller;

import com.amazon.shortlink.algorithm.TokenBucketRateLimiter;
import com.amazon.shortlink.dto.ShortenRequest;
import com.amazon.shortlink.dto.ShortenResponse;
import com.amazon.shortlink.dto.UrlStatsResponse;
import com.amazon.shortlink.exception.AliasAlreadyExistsException;
import com.amazon.shortlink.exception.UrlExpiredException;
import com.amazon.shortlink.exception.UrlNotFoundException;
import com.amazon.shortlink.service.ShortenerService;
import com.amazon.shortlink.service.UrlShortenerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UrlShortenerControllerTest {

    @Mock
    private ShortenerService shortenerService;

    private MockMvc mockMvc;
    private TokenBucketRateLimiter rateLimiter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Generous limit for normal tests: 50 capacity, 10/sec
        rateLimiter = new TokenBucketRateLimiter(50.0, 10.0);
        UrlShortenerController controller = new UrlShortenerController(shortenerService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RateLimitingFilter(rateLimiter))
                .build();
    }

    @Test
    @DisplayName("POST /shorten should return 201 Created with valid payload")
    void testShortenSuccess() throws Exception {
        ShortenRequest request = new ShortenRequest("https://aws.amazon.com", null, null, null);
        ShortenResponse response = new ShortenResponse("k9Z2a1x", "http://localhost:8080/k9Z2a1x", "https://aws.amazon.com", System.currentTimeMillis(), null, false);

        when(shortenerService.shortenUrl(any(ShortenRequest.class))).thenReturn(response);

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("k9Z2a1x"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/k9Z2a1x"))
                .andExpect(jsonPath("$.longUrl").value("https://aws.amazon.com"));
    }

    @Test
    @DisplayName("POST /shorten with blank URL should return 400 Bad Request")
    void testShortenBlankUrl() throws Exception {
        ShortenRequest request = new ShortenRequest("", null, null, null);

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ValidationError"));
    }

    @Test
    @DisplayName("POST /shorten with conflicting alias should return 409 Conflict")
    void testShortenAliasConflict() throws Exception {
        ShortenRequest request = new ShortenRequest("https://aws.amazon.com", "taken-alias", null, null);

        when(shortenerService.shortenUrl(any(ShortenRequest.class)))
                .thenThrow(new AliasAlreadyExistsException("taken-alias"));

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("AliasConflict"));
    }

    @Test
    @DisplayName("GET /{code} should redirect with 302 Found and Location header")
    void testRedirectSuccess() throws Exception {
        when(shortenerService.resolveAndTrack(eq("code123"), any(), any(), any(), any()))
                .thenReturn("https://aws.amazon.com/dynamodb");

        mockMvc.perform(get("/code123")
                        .header("Referer", "https://google.com")
                        .header("User-Agent", "Mozilla/5.0"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://aws.amazon.com/dynamodb"));
    }

    @Test
    @DisplayName("GET /{code} should return 404 Not Found when code does not exist")
    void testRedirectNotFound() throws Exception {
        when(shortenerService.resolveAndTrack(eq("notfound"), any(), any(), any(), any()))
                .thenThrow(new UrlNotFoundException("notfound"));

        mockMvc.perform(get("/notfound"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NotFound"));
    }

    @Test
    @DisplayName("GET /{code} should return 410 Gone when short URL has expired")
    void testRedirectExpired() throws Exception {
        when(shortenerService.resolveAndTrack(eq("expiredCode"), any(), any(), any(), any()))
                .thenThrow(new UrlExpiredException("expiredCode"));

        mockMvc.perform(get("/expiredCode"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("UrlExpired"));
    }

    @Test
    @DisplayName("GET /stats/{code} should return 200 OK with analytics data")
    void testGetStats() throws Exception {
        UrlStatsResponse stats = new UrlStatsResponse(
                "abc1234",
                "https://aws.amazon.com",
                42L,
                Map.of("2026-10-01", 42L),
                Map.of("DIRECT", 30L, "google.com", 12L),
                Map.of("Chrome", 35L, "Safari", 7L),
                Map.of("Windows", 30L, "macOS", 12L),
                System.currentTimeMillis(),
                null
        );

        when(shortenerService.getStats("abc1234")).thenReturn(stats);

        mockMvc.perform(get("/stats/abc1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("abc1234"))
                .andExpect(jsonPath("$.totalClicks").value(42));
    }

    @Test
    @DisplayName("RateLimitingFilter should return 429 Too Many Requests and Retry-After header when limit exceeded")
    void testRateLimitingExceeded() throws Exception {
        // Create strict rate limiter with 2 capacity and 0.1 refill/sec
        TokenBucketRateLimiter strictLimiter = new TokenBucketRateLimiter(2.0, 0.1);
        UrlShortenerController controller = new UrlShortenerController(shortenerService);
        MockMvc rateLimitedMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(new RateLimitingFilter(strictLimiter))
                .build();

        when(shortenerService.resolveAndTrack(eq("testCode"), any(), any(), any(), any()))
                .thenReturn("https://amazon.com");

        // 1st request -> OK (302)
        rateLimitedMvc.perform(get("/testCode").header("X-Forwarded-For", "203.0.113.195"))
                .andExpect(status().isFound());

        // 2nd request -> OK (302)
        rateLimitedMvc.perform(get("/testCode").header("X-Forwarded-For", "203.0.113.195"))
                .andExpect(status().isFound());

        // 3rd request -> 429 Too Many Requests + Retry-After
        rateLimitedMvc.perform(get("/testCode").header("X-Forwarded-For", "203.0.113.195"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("TooManyRequests"))
                .andExpect(jsonPath("$.status").value(429));
    }
}
