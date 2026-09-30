package com.amazon.shortlink.controller;

import com.amazon.shortlink.algorithm.TokenBucketRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * High-performance Servlet Filter enforcing per-IP rate limits using
 * our thread-safe TokenBucketRateLimiter.
 * 
 * Returns standard RFC 6585 HTTP 429 Too Many Requests with Retry-After header.
 */
@Component
@Order(1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final TokenBucketRateLimiter rateLimiter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitingFilter(TokenBucketRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip rate limiting on internal monitoring and docs endpoints
        return path.startsWith("/actuator") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = extractClientIp(request);
        TokenBucketRateLimiter.RateLimitResult result = rateLimiter.tryAcquire(clientIp);

        if (result.isAllowed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            Map<String, Object> errorBody = Map.of(
                    "error", "TooManyRequests",
                    "message", "Rate limit exceeded. Please retry after " + result.retryAfterSeconds() + " seconds.",
                    "status", 429,
                    "retryAfterSeconds", result.retryAfterSeconds(),
                    "timestamp", System.currentTimeMillis()
            );

            response.getWriter().write(objectMapper.writeValueAsString(errorBody));
        }
    }

    public static String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // In AWS ALB/CloudFront, X-Forwarded-For contains "client, proxy1, proxy2"
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}
