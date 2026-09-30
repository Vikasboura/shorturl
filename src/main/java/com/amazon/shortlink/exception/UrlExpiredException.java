package com.amazon.shortlink.exception;

public class UrlExpiredException extends RuntimeException {
    public UrlExpiredException(String shortCode) {
        super("Short URL has expired for code: " + shortCode);
    }
}
