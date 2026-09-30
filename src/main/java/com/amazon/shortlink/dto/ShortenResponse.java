package com.amazon.shortlink.dto;

public record ShortenResponse(
        String shortCode,
        String shortUrl,
        String longUrl,
        Long createdAt,
        Long expiresAt,
        boolean isCustomAlias
) {}
