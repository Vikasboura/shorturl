package com.amazon.shortlink.exception;

public class UrlNotFoundException extends RuntimeException {
    public UrlNotFoundException(String shortCode) {
        super("Short URL not found for code: " + shortCode);
    }
}
