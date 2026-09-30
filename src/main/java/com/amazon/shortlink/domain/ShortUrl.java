package com.amazon.shortlink.domain;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;

/**
 * DynamoDB Entity representing a shortened URL.
 * 
 * Partition Key: short_code
 * TTL Attribute: expires_at (epoch seconds)
 */
@DynamoDbBean
public class ShortUrl {

    private String shortCode;
    private String longUrl;
    private Long createdAt;
    private Long expiresAt; // TTL in epoch seconds
    private Boolean customAlias;
    private String creatorId;

    public ShortUrl() {
    }

    public ShortUrl(String shortCode, String longUrl, Long createdAt, Long expiresAt, Boolean customAlias, String creatorId) {
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.createdAt = createdAt != null ? createdAt : Instant.now().toEpochMilli();
        this.expiresAt = expiresAt;
        this.customAlias = customAlias != null ? customAlias : false;
        this.creatorId = creatorId;
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("short_code")
    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    @DynamoDbAttribute("long_url")
    public String getLongUrl() {
        return longUrl;
    }

    public void setLongUrl(String longUrl) {
        this.longUrl = longUrl;
    }

    @DynamoDbAttribute("created_at")
    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbAttribute("expires_at")
    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }

    @DynamoDbAttribute("is_custom_alias")
    public Boolean getCustomAlias() {
        return customAlias;
    }

    public void setCustomAlias(Boolean customAlias) {
        this.customAlias = customAlias;
    }

    @DynamoDbAttribute("creator_id")
    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }

    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return Instant.now().getEpochSecond() > expiresAt;
    }
}
