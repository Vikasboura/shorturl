# Amazon Interview Defense Guide: URL Shortener System Design

## 1. Top Interview Questions & Defensible Answers

### Q1: Why use 7-character random Base62 instead of an auto-incrementing ID?
**Answer:**
- **Predictability & Security**: Auto-incrementing counters (e.g. Snowflake or relational sequences) produce sequential short URLs (`/1`, `/2`, `/3`). An attacker or competitor could easily crawl every shortened URL or calculate the link creation rate over time, leaking business-sensitive data.
- **Coordination Bottleneck**: In a distributed multi-region system, centralized sequence generators require distributed consensus (e.g., ZooKeeper or range allocation servers), which creates an operational bottleneck and single point of failure.
- **Collision Math**: With 62 characters (`[0-9a-zA-Z]`), 7 characters yield $62^7 = 3,521,614,606,208$ (~3.52 trillion) combinations. Even with 100 million entries, the collision probability is $< 0.14\%$. When collisions occur, DynamoDB's atomic `attribute_not_exists(short_code)` conditional write rejects the put, triggering an automatic retry with jitter.

---

### Q2: How do you handle the "Hot Key" Problem in DynamoDB and the App Tier?
**Answer:**
When a viral link (e.g., posted on prime-time TV or Twitter) receives 100,000 requests per second, a single partition key in DynamoDB would normally throttle due to partition throughput limits (1,000 WCU or 3,000 RCU per partition).
We solve this in two layers:
1. **Tier 1 (In-Memory LRU Cache)**:
   - Our custom in-memory LRU cache stores hot links in app memory.
   - For a hot link, 99%+ of reads hit local memory, avoiding DynamoDB entirely ($< 1$ ms latency).
2. **Tier 2 (CDN / CloudFront Edge)**:
   - For public links, `Cache-Control: public, max-age=300` or CloudFront edge caching can absorb millions of identical redirects globally at CloudFront PoPs without hitting origin servers.

---

### Q3: Why implement a custom Token Bucket rate limiter instead of Redis or Guava?
**Answer:**
- **Fine-Grained Latency Control**: Redis-based rate limiting requires a network round-trip ($1-3$ ms) for every incoming request. An in-memory token bucket executes in nanoseconds via atomic memory operations (`ConcurrentHashMap` + atomic time/token math).
- **Graceful Burst Absorption**: Unlike Fixed Window or Sliding Window log algorithms (which either suffer from boundary burst issues or high memory overhead), Token Bucket naturally handles short bursts up to the bucket capacity while maintaining a strict long-term average refill rate.
- **Interview Defense**: Demonstrates deep understanding of thread-safety, CAS operations, concurrency primitives, and RFC 6585 compliance (`429 Too Many Requests` + `Retry-After`).

---

### Q4: Why asynchronous click analytics instead of synchronous writes?
**Answer:**
- The primary contract of a URL shortener is **sub-15ms redirect latency**.
- If click tracking were synchronous, every redirect would block on a DynamoDB `PutItem` write ($10-30$ ms) and be vulnerable to database write throttling or outages.
- By using an asynchronous worker queue (`ThreadPoolTaskExecutor` with bounded queue and `CallerRunsPolicy`), the user immediately receives HTTP `302 Found`, while telemetry is persisted in the background without user-perceptible delay.

---

### Q5: How does this scale to 100M+ Daily Active Users (DAU)?
**Answer:**
1. **Read/Write Ratio**: ~100:1. Scale the read path independently.
2. **Caching**: Multi-tier caching (CloudFront Edge -> Application LRU Cache -> DynamoDB).
3. **Database Partitioning**: DynamoDB automatically partitions by `short_code` hash across SSD storage nodes.
4. **Analytics Pipeline**: At massive scale, replace direct DynamoDB writes with an Amazon Kinesis Data Stream or SQS buffer consumed by AWS Lambda / Apache Flink for real-time aggregation and S3 data lake storage (Parquet + Athena).
