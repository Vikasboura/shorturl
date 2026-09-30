package com.amazon.shortlink.service;

import com.amazon.shortlink.algorithm.CodeGenerator;
import com.amazon.shortlink.algorithm.CustomLruCache;
import com.amazon.shortlink.algorithm.ShortCodeGenerator;
import com.amazon.shortlink.domain.ShortUrl;
import com.amazon.shortlink.dto.ShortenRequest;
import com.amazon.shortlink.dto.ShortenResponse;
import com.amazon.shortlink.exception.AliasAlreadyExistsException;
import com.amazon.shortlink.exception.InvalidUrlException;
import com.amazon.shortlink.exception.UrlExpiredException;
import com.amazon.shortlink.exception.UrlNotFoundException;
import com.amazon.shortlink.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    @Mock
    private ShortUrlRepository shortUrlRepository;

    @Mock
    private CodeGenerator shortCodeGenerator;

    @Mock
    private AnalyticsService analyticsService;

    private CustomLruCache<String, ShortUrl> cache;
    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        cache = new CustomLruCache<>(100);
        service = new UrlShortenerService(shortUrlRepository, shortCodeGenerator, analyticsService, cache);
        ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:8080");
    }

    @Test
    @DisplayName("Should successfully shorten URL with random 7-character code")
    void testShortenUrlRandomCode() {
        when(shortCodeGenerator.generate()).thenReturn("k9Z2a1x");
        when(shortUrlRepository.saveWithCondition(any(ShortUrl.class))).thenReturn(true);

        ShortenRequest request = new ShortenRequest("https://aws.amazon.com/dynamodb", null, null, "user1");
        ShortenResponse response = service.shortenUrl(request);

        assertThat(response.shortCode()).isEqualTo("k9Z2a1x");
        assertThat(response.shortUrl()).isEqualTo("http://localhost:8080/k9Z2a1x");
        assertThat(response.longUrl()).isEqualTo("https://aws.amazon.com/dynamodb");
        assertThat(response.isCustomAlias()).isFalse();

        // Ensure item was cached
        assertThat(cache.get("k9Z2a1x")).isNotNull();
    }

    @Test
    @DisplayName("Should retry on collision and succeed when second attempt passes")
    void testShortenUrlCollisionRetry() {
        when(shortCodeGenerator.generate()).thenReturn("coll1", "coll2");
        when(shortUrlRepository.saveWithCondition(any(ShortUrl.class)))
                .thenReturn(false)  // First attempt collided
                .thenReturn(true);  // Second attempt succeeded

        ShortenRequest request = new ShortenRequest("https://aws.amazon.com", null, null, null);
        ShortenResponse response = service.shortenUrl(request);

        assertThat(response.shortCode()).isEqualTo("coll2");
        verify(shortCodeGenerator, times(2)).generate();
        verify(shortUrlRepository, times(2)).saveWithCondition(any(ShortUrl.class));
    }

    @Test
    @DisplayName("Should support custom alias when valid and available")
    void testShortenUrlCustomAliasSuccess() {
        when(shortCodeGenerator.isValidCustomAlias("my-campaign")).thenReturn(true);
        when(shortUrlRepository.saveWithCondition(any(ShortUrl.class))).thenReturn(true);

        ShortenRequest request = new ShortenRequest("https://amazon.com/deals", "my-campaign", 3600L, "marketing");
        ShortenResponse response = service.shortenUrl(request);

        assertThat(response.shortCode()).isEqualTo("my-campaign");
        assertThat(response.isCustomAlias()).isTrue();
        assertThat(response.expiresAt()).isNotNull();
    }

    @Test
    @DisplayName("Should throw AliasAlreadyExistsException when custom alias is taken")
    void testShortenUrlCustomAliasDuplicate() {
        when(shortCodeGenerator.isValidCustomAlias("taken-alias")).thenReturn(true);
        when(shortUrlRepository.saveWithCondition(any(ShortUrl.class))).thenReturn(false);

        ShortenRequest request = new ShortenRequest("https://amazon.com", "taken-alias", null, null);

        assertThatThrownBy(() -> service.shortenUrl(request))
                .isInstanceOf(AliasAlreadyExistsException.class)
                .hasMessageContaining("taken-alias");
    }

    @Test
    @DisplayName("Should reject invalid URL schemes")
    void testShortenUrlInvalidScheme() {
        ShortenRequest request = new ShortenRequest("ftp://malicious.site", null, null, null);

        assertThatThrownBy(() -> service.shortenUrl(request))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("scheme must be http or https");
    }

    @Test
    @DisplayName("Cache hit should return URL and bypass DynamoDB completely")
    void testResolveAndTrackCacheHit() {
        ShortUrl cachedUrl = new ShortUrl("cached1", "https://aws.amazon.com", System.currentTimeMillis(), null, false, null);
        cache.put("cached1", cachedUrl);

        String result = service.resolveAndTrack("cached1", "https://google.com", "Mozilla/5.0", "1.2.3.4", "US");

        assertThat(result).isEqualTo("https://aws.amazon.com");
        // Verify DynamoDB was NEVER contacted!
        verifyNoInteractions(shortUrlRepository);
        // Verify async click telemetry was triggered
        verify(analyticsService, times(1)).recordClickAsync(eq("cached1"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Cache miss should query DynamoDB, populate cache, and record click")
    void testResolveAndTrackCacheMiss() {
        ShortUrl dbUrl = new ShortUrl("miss1", "https://aws.amazon.com/s3", System.currentTimeMillis(), null, false, null);
        when(shortUrlRepository.findByShortCode("miss1")).thenReturn(Optional.of(dbUrl));

        String result = service.resolveAndTrack("miss1", null, "curl/7.68.0", "5.6.7.8", "US");

        assertThat(result).isEqualTo("https://aws.amazon.com/s3");
        verify(shortUrlRepository, times(1)).findByShortCode("miss1");
        // Verify now stored in cache
        assertThat(cache.get("miss1")).isNotNull();
        verify(analyticsService, times(1)).recordClickAsync(eq("miss1"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should throw UrlExpiredException when link has expired")
    void testResolveAndTrackExpired() {
        long pastEpochSecond = Instant.now().getEpochSecond() - 3600;
        ShortUrl expiredUrl = new ShortUrl("exp1", "https://amazon.com", System.currentTimeMillis() - 7200000, pastEpochSecond, false, null);
        when(shortUrlRepository.findByShortCode("exp1")).thenReturn(Optional.of(expiredUrl));

        assertThatThrownBy(() -> service.resolveAndTrack("exp1", null, null, null, null))
                .isInstanceOf(UrlExpiredException.class);
    }

    @Test
    @DisplayName("Should throw UrlNotFoundException when short code does not exist")
    void testResolveAndTrackNotFound() {
        when(shortUrlRepository.findByShortCode("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveAndTrack("unknown", null, null, null, null))
                .isInstanceOf(UrlNotFoundException.class);
    }
}
