import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.remote.RemoteBucketState;
import io.github.bucket4j.redis.redisson.cas.RedissonBasedProxyManager;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Real Bucket4j Implementation with Redis (Distributed)
 *
 * This shows how to use Bucket4j with Redis for distributed rate limiting
 * across multiple application instances/servers.
 *
 * Dependencies required:
 *
 * Maven:
 * <dependency>
 *     <groupId>com.bucket4j</groupId>
 *     <artifactId>bucket4j-core</artifactId>
 *     <version>8.7.0</version>
 * </dependency>
 * <dependency>
 *     <groupId>com.bucket4j</groupId>
 *     <artifactId>bucket4j-redis</artifactId>
 *     <version>8.7.0</version>
 * </dependency>
 * <dependency>
 *     <groupId>org.redisson</groupId>
 *     <artifactId>redisson</artifactId>
 *     <version>3.23.5</version>
 * </dependency>
 *
 * Gradle:
 * implementation 'com.bucket4j:bucket4j-core:8.7.0'
 * implementation 'com.bucket4j:bucket4j-redis:8.7.0'
 * implementation 'org.redisson:redisson:3.23.5'
 */
public class RealBucket4jRedisImplementation {

    // ==================== SETUP REDIS CONNECTION ====================

    /**
     * Create Redisson client for Redis connection
     */
    public static RedissonClient createRedissonClient() {
        Config config = new Config();

        // Single server configuration
        config.useSingleServer()
                .setAddress("redis://localhost:6379")
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(10)
                .setTimeout(3000);

        // For Redis with authentication:
        // config.useSingleServer()
        //     .setAddress("redis://localhost:6379")
        //     .setPassword("your-password");

        // For Redis Cluster:
        // config.useClusterServers()
        //     .addNodeAddress("redis://127.0.0.1:7001", "redis://127.0.0.1:7002");

        return Redisson.create(config);
    }

    // ==================== EXAMPLE 1: BASIC REDIS BUCKET ====================

    /**
     * Create a distributed bucket stored in Redis
     */
    public static void example1_BasicRedisBucket(RedissonClient redisson) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 1: Basic Redis Bucket");
        System.out.println("=".repeat(70));

        // Create ProxyManager for Redis
        ProxyManager<String> proxyManager = RedissonBasedProxyManager.builderFor(redisson)
                .build();

        // Define bucket configuration
        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(10, Duration.ofSeconds(1)))
                .build();

        // Get bucket for specific user (stored in Redis)
        Bucket bucket = proxyManager.builder()
                .build("user:12345", config);

        // Use it just like local bucket!
        System.out.println("Available tokens: " + bucket.getAvailableTokens());

        boolean consumed = bucket.tryConsume(3);
        System.out.println("Consumed 3 tokens: " + consumed);
        System.out.println("Remaining tokens: " + bucket.getAvailableTokens());

        // This state is now in Redis and shared across all servers!
    }

    // ==================== EXAMPLE 2: PER-USER RATE LIMITING ====================

    /**
     * Different rate limits for different users
     */
    public static void example2_PerUserRateLimiting(RedissonClient redisson) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 2: Per-User Rate Limiting");
        System.out.println("=".repeat(70));

        ProxyManager<String> proxyManager = RedissonBasedProxyManager.builderFor(redisson)
                .build();

        // Same configuration for all users
        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(100, Duration.ofMinutes(1)))
                .build();

        // Different buckets for different users
        Bucket user1Bucket = proxyManager.builder().build("user:1", config);
        Bucket user2Bucket = proxyManager.builder().build("user:2", config);
        Bucket user3Bucket = proxyManager.builder().build("user:3", config);

        // Each user has independent state in Redis
        user1Bucket.tryConsume(10);
        user2Bucket.tryConsume(5);
        user3Bucket.tryConsume(20);

        System.out.println("User 1 remaining: " + user1Bucket.getAvailableTokens());
        System.out.println("User 2 remaining: " + user2Bucket.getAvailableTokens());
        System.out.println("User 3 remaining: " + user3Bucket.getAvailableTokens());
    }

    // ==================== EXAMPLE 3: TIERED RATE LIMITING ====================

    /**
     * Different rate limits based on user tier (free vs premium)
     */
    public static void example3_TieredRateLimiting(RedissonClient redisson) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 3: Tiered Rate Limiting");
        System.out.println("=".repeat(70));

        ProxyManager<String> proxyManager = RedissonBasedProxyManager.builderFor(redisson)
                .build();

        // Free tier: 100 requests per hour
        BucketConfiguration freeConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(100, Duration.ofHours(1)))
                .build();

        // Premium tier: 10,000 requests per hour
        BucketConfiguration premiumConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(10_000, Duration.ofHours(1)))
                .build();

        // Enterprise tier: 1,000,000 requests per hour
        BucketConfiguration enterpriseConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(1_000_000, Duration.ofHours(1)))
                .build();

        // Create buckets for different user tiers
        Bucket freeUser = proxyManager.builder().build("user:free:alice", freeConfig);
        Bucket premiumUser = proxyManager.builder().build("user:premium:bob", premiumConfig);
        Bucket enterpriseUser = proxyManager.builder().build("user:enterprise:carol", enterpriseConfig);

        System.out.println("Free user limit: " + freeUser.getAvailableTokens());
        System.out.println("Premium user limit: " + premiumUser.getAvailableTokens());
        System.out.println("Enterprise user limit: " + enterpriseUser.getAvailableTokens());
    }

    // ==================== EXAMPLE 4: API ENDPOINT RATE LIMITING ====================

    /**
     * Rate limit per API endpoint
     */
    public static void example4_PerEndpointRateLimiting(RedissonClient redisson) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 4: Per-Endpoint Rate Limiting");
        System.out.println("=".repeat(70));

        ProxyManager<String> proxyManager = RedissonBasedProxyManager.builderFor(redisson)
                .build();

        String userId = "user:12345";

        // Different limits for different endpoints
        BucketConfiguration searchConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(10, Duration.ofSeconds(1)))  // 10/sec for search
                .build();

        BucketConfiguration uploadConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(5, Duration.ofMinutes(1)))  // 5/min for upload
                .build();

        BucketConfiguration downloadConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(100, Duration.ofMinutes(1)))  // 100/min for download
                .build();

        // Create buckets with composite keys
        Bucket searchBucket = proxyManager.builder()
                .build(userId + ":search", searchConfig);
        Bucket uploadBucket = proxyManager.builder()
                .build(userId + ":upload", uploadConfig);
        Bucket downloadBucket = proxyManager.builder()
                .build(userId + ":download", downloadConfig);

        System.out.println("Search endpoint: " + searchBucket.getAvailableTokens());
        System.out.println("Upload endpoint: " + uploadBucket.getAvailableTokens());
        System.out.println("Download endpoint: " + downloadBucket.getAvailableTokens());
    }

    // ==================== EXAMPLE 5: MULTI-LAYER RATE LIMITING ====================

    /**
     * Apply multiple rate limits simultaneously
     * (burst limit + sustained limit)
     */
    public static void example5_MultiLayerRateLimiting(RedissonClient redisson) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 5: Multi-Layer Rate Limiting");
        System.out.println("=".repeat(70));

        ProxyManager<String> proxyManager = RedissonBasedProxyManager.builderFor(redisson)
                .build();

        // Apply multiple limits:
        // - Burst: 20 requests per second (handle spikes)
        // - Sustained: 1000 requests per hour (prevent abuse)
        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(20, Duration.ofSeconds(1)))     // Burst limit
                .addLimit(Bandwidth.simple(1000, Duration.ofHours(1)))     // Hourly limit
                .addLimit(Bandwidth.simple(10_000, Duration.ofDays(1)))    // Daily limit
                .build();

        Bucket bucket = proxyManager.builder().build("user:multilayer", config);

        // All limits are checked on each consumption
        System.out.println("Available tokens: " + bucket.getAvailableTokens());

        // Try to consume 25 (exceeds 20/sec burst limit)
        boolean consumed = bucket.tryConsume(25);
        System.out.println("Try consume 25 (burst limit is 20): " + consumed);  // false
    }

    // ==================== EXAMPLE 6: DYNAMIC CONFIGURATION ====================

    /**
     * Change rate limits at runtime
     */
    public static void example6_DynamicConfiguration(RedissonClient redisson) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 6: Dynamic Configuration");
        System.out.println("=".repeat(70));

        ProxyManager<String> proxyManager = RedissonBasedProxyManager.builderFor(redisson)
                .build();

        // Start with free tier
        BucketConfiguration initialConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(100, Duration.ofHours(1)))
                .build();

        Bucket bucket = proxyManager.builder().build("user:upgrade", initialConfig);
        System.out.println("Initial (free tier): " + bucket.getAvailableTokens());

        // User upgrades to premium!
        BucketConfiguration premiumConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(10_000, Duration.ofHours(1)))
                .build();

        bucket.replaceConfiguration(premiumConfig,
                io.github.bucket4j.TokensInheritanceStrategy.PROPORTIONALLY);

        System.out.println("After upgrade (premium tier): " + bucket.getAvailableTokens());
    }

    // ==================== EXAMPLE 7: IP-BASED RATE LIMITING ====================

    /**
     * Rate limit by IP address (for anonymous users)
     */
    public static void example7_IpBasedRateLimiting(RedissonClient redisson) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 7: IP-Based Rate Limiting");
        System.out.println("=".repeat(70));

        ProxyManager<String> proxyManager = RedissonBasedProxyManager.builderFor(redisson)
                .build();

        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(100, Duration.ofHours(1)))
                .build();

        // Rate limit by IP
        String clientIp = "192.168.1.100";
        Bucket ipBucket = proxyManager.builder()
                .build("ip:" + clientIp, config);

        System.out.println("IP bucket for " + clientIp + ": " + ipBucket.getAvailableTokens());

        // Simulate multiple requests from same IP
        for (int i = 0; i < 5; i++) {
            ipBucket.tryConsume(1);
        }

        System.out.println("After 5 requests: " + ipBucket.getAvailableTokens());
    }

    // ==================== EXAMPLE 8: CONDITIONAL RATE LIMITING ====================

    /**
     * Different limits based on request characteristics
     */
    public static void example8_ConditionalRateLimiting(RedissonClient redisson) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 8: Conditional Rate Limiting");
        System.out.println("=".repeat(70));

        ProxyManager<String> proxyManager = RedissonBasedProxyManager.builderFor(redisson)
                .build();

        String userId = "user:12345";
        boolean isAuthenticated = true;
        boolean isPremium = false;

        // Choose configuration based on user status
        BucketConfiguration config;

        if (!isAuthenticated) {
            // Anonymous: Very restrictive
            config = BucketConfiguration.builder()
                    .addLimit(Bandwidth.simple(10, Duration.ofHours(1)))
                    .build();
            System.out.println("Using anonymous rate limit: 10/hour");
        } else if (isPremium) {
            // Premium: Generous
            config = BucketConfiguration.builder()
                    .addLimit(Bandwidth.simple(10_000, Duration.ofHours(1)))
                    .build();
            System.out.println("Using premium rate limit: 10,000/hour");
        } else {
            // Authenticated free: Moderate
            config = BucketConfiguration.builder()
                    .addLimit(Bandwidth.simple(1000, Duration.ofHours(1)))
                    .build();
            System.out.println("Using free tier rate limit: 1,000/hour");
        }

        Bucket bucket = proxyManager.builder().build(userId, config);
        System.out.println("Available tokens: " + bucket.getAvailableTokens());
    }

    // ==================== EXAMPLE 9: COST-BASED RATE LIMITING ====================

    /**
     * Different operations consume different amounts of tokens
     */
    public static void example9_CostBasedRateLimiting(RedissonClient redisson) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 9: Cost-Based Rate Limiting");
        System.out.println("=".repeat(70));

        ProxyManager<String> proxyManager = RedissonBasedProxyManager.builderFor(redisson)
                .build();

        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(100, Duration.ofMinutes(1)))
                .build();

        Bucket bucket = proxyManager.builder().build("user:cost", config);

        // Different operations have different costs
        long GET_COST = 1;      // Read: 1 token
        long POST_COST = 5;     // Write: 5 tokens
        long SEARCH_COST = 10;  // Search: 10 tokens
        long EXPORT_COST = 50;  // Export: 50 tokens

        System.out.println("Initial tokens: " + bucket.getAvailableTokens());

        // Simulate operations
        bucket.tryConsume(GET_COST);
        System.out.println("After GET (cost 1): " + bucket.getAvailableTokens());

        bucket.tryConsume(POST_COST);
        System.out.println("After POST (cost 5): " + bucket.getAvailableTokens());

        bucket.tryConsume(SEARCH_COST);
        System.out.println("After SEARCH (cost 10): " + bucket.getAvailableTokens());

        boolean canExport = bucket.tryConsume(EXPORT_COST);
        System.out.println("Can EXPORT (cost 50)? " + canExport);
    }

    // ==================== EXAMPLE 10: VERBOSE CONSUMPTION ====================

    /**
     * Get detailed information about rate limit status
     */
    public static void example10_VerboseConsumption(RedissonClient redisson) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 10: Verbose Consumption");
        System.out.println("=".repeat(70));

        ProxyManager<String> proxyManager = RedissonBasedProxyManager.builderFor(redisson)
                .build();

        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(5, Duration.ofSeconds(10)))
                .build();

        Bucket bucket = proxyManager.builder().build("user:verbose", config);

        // Consume some tokens
        bucket.tryConsume(4);

        // Try to consume more than available
        var probe = bucket.tryConsumeAndReturnRemaining(3);

        System.out.println("Consumption attempt for 3 tokens:");
        System.out.println("  Consumed: " + probe.isConsumed());
        System.out.println("  Remaining: " + probe.getRemainingTokens());
        System.out.println("  Wait time: " + probe.getNanosToWaitForRefill() / 1_000_000_000.0 + " seconds");

        if (!probe.isConsumed()) {
            System.out.println("  → Request would be rejected (429 Too Many Requests)");
            System.out.println("  → Retry-After: " + Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0) + " seconds");
        }
    }

    // ==================== MAIN DEMONSTRATION ====================

    public static void main(String[] args) {
        System.out.println("\n");
        System.out.println("█".repeat(70));
        System.out.println("██           REAL BUCKET4J REDIS IMPLEMENTATION                   ██");
        System.out.println("█".repeat(70));

        // Create Redis connection
        RedissonClient redisson = null;

        try {
            System.out.println("\nConnecting to Redis...");
            redisson = createRedissonClient();
            System.out.println("✓ Connected to Redis");

            // Run all examples
            example1_BasicRedisBucket(redisson);
            example2_PerUserRateLimiting(redisson);
            example3_TieredRateLimiting(redisson);
            example4_PerEndpointRateLimiting(redisson);
            example5_MultiLayerRateLimiting(redisson);
            example6_DynamicConfiguration(redisson);
            example7_IpBasedRateLimiting(redisson);
            example8_ConditionalRateLimiting(redisson);
            example9_CostBasedRateLimiting(redisson);
            example10_VerboseConsumption(redisson);

            System.out.println("\n" + "█".repeat(70));
            System.out.println("All examples completed!");
            System.out.println("█".repeat(70) + "\n");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("\nMake sure Redis is running on localhost:6379");
            System.err.println("Start Redis with: redis-server");
            e.printStackTrace();
        } finally {
            if (redisson != null) {
                redisson.shutdown();
                System.out.println("✓ Redis connection closed");
            }
        }
    }
}

