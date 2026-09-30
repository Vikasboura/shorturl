package com.amazon.shortlink.dto;

import jakarta.validation.constraints.NotBlank;

public record ShortenRequest(
        @NotBlank(message = "URL must not be blank")
        String url,
        String customAlias,
        Long ttlSeconds,
        String creatorId
) {}
