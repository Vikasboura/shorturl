package com.amazon.shortlink.domain;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * DynamoDB Entity representing a link access/click event.
 * 
 * Partition Key: short_code
 * Sort Key: timestamp_event_id (ISO instant + # + UUID)
 * Global Secondary Index: DateIndex (PK: short_code, SK: date_str)
 */
@DynamoDbBean
public class ClickEvent {

    public static final String DATE_INDEX = "DateIndex";

    private String shortCode;
    private String timestampEventId;
    private String dateStr; // YYYY-MM-DD for fast date range aggregations
    private String referrer;
    private String userAgent;
    private String browser;
    private String os;
    private String ipHash; // SHA-256 anonymized IP for privacy
    private String country;

    public ClickEvent() {
    }

    public ClickEvent(String shortCode, String timestampEventId, String dateStr,
                      String referrer, String userAgent, String browser,
                      String os, String ipHash, String country) {
        this.shortCode = shortCode;
        this.timestampEventId = timestampEventId;
        this.dateStr = dateStr;
        this.referrer = referrer != null ? referrer : "DIRECT";
        this.userAgent = userAgent != null ? userAgent : "Unknown";
        this.browser = browser != null ? browser : "Other";
        this.os = os != null ? os : "Other";
        this.ipHash = ipHash;
        this.country = country != null ? country : "UNKNOWN";
    }

    @DynamoDbPartitionKey
    @DynamoDbSecondaryPartitionKey(indexNames = DATE_INDEX)
    @DynamoDbAttribute("short_code")
    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("timestamp_event_id")
    public String getTimestampEventId() {
        return timestampEventId;
    }

    public void setTimestampEventId(String timestampEventId) {
        this.timestampEventId = timestampEventId;
    }

    @DynamoDbSecondarySortKey(indexNames = DATE_INDEX)
    @DynamoDbAttribute("date_str")
    public String getDateStr() {
        return dateStr;
    }

    public void setDateStr(String dateStr) {
        this.dateStr = dateStr;
    }

    @DynamoDbAttribute("referrer")
    public String getReferrer() {
        return referrer;
    }

    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }

    @DynamoDbAttribute("user_agent")
    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    @DynamoDbAttribute("browser")
    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    @DynamoDbAttribute("os")
    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    @DynamoDbAttribute("ip_hash")
    public String getIpHash() {
        return ipHash;
    }

    public void setIpHash(String ipHash) {
        this.ipHash = ipHash;
    }

    @DynamoDbAttribute("country")
    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}
