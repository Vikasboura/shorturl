# DynamoDB Data Modeling & Access Patterns

## 1. Table Definitions

### 1.1 Links Table (`shortlink-urls`)
Stores shortened link metadata, target destination, creation metadata, and TTL configuration.

- **Billing Mode**: `PAY_PER_REQUEST` (On-Demand / Free Tier safe)
- **Partition Key (PK)**: `short_code` (String, e.g. `k9Z2a1x`)
- **Sort Key (SK)**: None
- **TTL Attribute**: `expires_at` (Number, Unix epoch seconds)

| Attribute | Type | Description |
| :--- | :--- | :--- |
| `short_code` (PK) | String | 7-character Base62 string or custom vanity alias |
| `long_url` | String | Target URL destination |
| `created_at` | Number | Creation timestamp in epoch milliseconds |
| `expires_at` | Number | Expiration timestamp in epoch seconds (TTL monitored) |
| `is_custom_alias` | Boolean | Flag indicating whether this was user-chosen or generated |
| `creator_id` | String | Identifier of creator / API key |

#### Access Patterns:
1. **Fetch Short Link (Redirect Path)**
   - Query: `GetItem(short_code)`
   - Consistency: Eventually Consistent (or Strongly Consistent for custom aliases)
   - Latency: Single-digit ms ($O(1)$)
2. **Create Short Link (Shorten Path)**
   - Query: `PutItem` with condition `attribute_not_exists(short_code)`
   - Protects against duplicate generation and custom alias collision.

---

### 1.2 Click Events Table (`shortlink-clicks`)
Stores granular click event stream for real-time analytics aggregation.

- **Billing Mode**: `PAY_PER_REQUEST`
- **Partition Key (PK)**: `short_code` (String)
- **Sort Key (SK)**: `timestamp_event_id` (String, e.g. `2026-10-01T12:00:00.123Z#f47ac10b`)
- **Global Secondary Index (GSI)**: `DateIndex`
  - PK: `short_code` (String)
  - SK: `date_str` (String, `YYYY-MM-DD`)

| Attribute | Type | Description |
| :--- | :--- | :--- |
| `short_code` (PK) | String | Associated short code |
| `timestamp_event_id` (SK) | String | ISO 8601 timestamp + UUID suffix to avoid collisions |
| `date_str` (GSI SK) | String | Date string for rapid range scans (`YYYY-MM-DD`) |
| `referrer` | String | HTTP Referer header or "DIRECT" |
| `user_agent` | String | Raw or parsed User-Agent string |
| `browser` | String | Parsed browser family (Chrome, Safari, Firefox, Edge, Other) |
| `os` | String | Parsed OS (Windows, macOS, Linux, iOS, Android, Other) |
| `ip_hash` | String | SHA-256 salted hash of client IP for privacy |
| `country` | String | Inferred or CloudFront geolocation country code |

#### Access Patterns:
1. **Ingest Click Event (Async Analytics Path)**
   - Query: `PutItem`
   - Non-blocking execution via thread pool.
2. **Retrieve Analytics for Short Code**
   - Query: `Query(PK = short_code)`
   - Retrieves recent events for aggregation (total clicks, top referrers, browser breakdown).
3. **Retrieve Daily Aggregation**
   - Query: `Query(IndexName = DateIndex, PK = short_code, SK between date1 and date2)`
