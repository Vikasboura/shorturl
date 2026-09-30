package com.amazon.shortlink.dto;

import java.time.Instant;

/**
 * Standard Amazon-style error envelope.
 */
public record ErrorResponse(
        String error,
        String message,
        int status,
        long timestamp
) {
    public static ErrorResponse of(String error, String message, int status) {
        return new ErrorResponse(error, message, status, Instant.now().toEpochMilli());
    }
}
