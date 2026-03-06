import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.local.LocalBucket;
import io.github.bucket4j.local.SynchronizationStrategy;

import java.time.Duration;

/**
 * Real Bucket4j Implementation
 * Using actual Bucket4j library classes instead of simplified version
 *
 * Dependencies required (add to pom.xml or build.gradle):
 *
 * Maven:
 * <dependency>
 *     <groupId>com.bucket4j</groupId>
 *     <artifactId>bucket4j-core</artifactId>
 *     <version>8.7.0</version>
 * </dependency>
 *
 * Gradle:
 * implementation 'com.bucket4j:bucket4j-core:8.7.0'
 */
public class RealBucket4jImplementation {

    // ==================== EXAMPLE 1: BASIC LOCAL BUCKET ====================

    /**
     * Simplest way to create a bucket
     * Rate limit: 10 tokens per 1 second
     */
    public static void example1_BasicBucket() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 1: Basic Local Bucket");
        System.out.println("=".repeat(70));

        // Create bucket using builder pattern
        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.simple(10, Duration.ofSeconds(1)))
                .build();

        // Try to consume tokens
        System.out.println("Available tokens: " + bucket.getAvailableTokens());

        boolean consumed = bucket.tryConsume(3);
        System.out.println("Consumed 3 tokens: " + consumed);
        System.out.println("Remaining tokens: " + bucket.getAvailableTokens());

        // Try to consume more than available
        consumed = bucket.tryConsume(10);
        System.out.println("Try consume 10 tokens: " + consumed);  // false
    }

    // ==================== EXAMPLE 2: USING BUCKET CONFIGURATION ====================

    /**
     * Using BucketConfiguration (reusable across multiple buckets)
     */
    public static void example2_BucketConfiguration() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 2: Reusable BucketConfiguration");
        System.out.println("=".repeat(70));

        // Create configuration once
        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(5, Duration.ofSeconds(10)))
                .build();

        // Use same configuration for multiple buckets (e.g., per user)
        Bucket userBucket1 = Bucket.builder()
                .addLimit(Bandwidth.simple(5, Duration.ofSeconds(10)))
                .build();

        Bucket userBucket2 = Bucket.builder()
                .addLimit(Bandwidth.simple(5, Duration.ofSeconds(10)))
                .build();

        // Each bucket has independent state
        userBucket1.tryConsume(3);
        userBucket2.tryConsume(1);

        System.out.println("User 1 remaining: " + userBucket1.getAvailableTokens());  // 2
        System.out.println("User 2 remaining: " + userBucket2.getAvailableTokens());  // 4
    }

    // ==================== EXAMPLE 3: CLASSIC REFILL (GREEDY) ====================

    /**
     * Classic refill: Tokens refill at a steady rate
     * Greedy refill: Tries to add as many tokens as possible immediately
     */
    public static void example3_ClassicRefill() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 3: Classic (Greedy) Refill");
        System.out.println("=".repeat(70));

        // Refill 5 tokens every 10 seconds (greedy)
        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.classic(5, Refill.greedy(5, Duration.ofSeconds(10))))
                .build();

        System.out.println("Initial tokens: " + bucket.getAvailableTokens());
        bucket.tryConsume(5);
        System.out.println("After consuming 5: " + bucket.getAvailableTokens());

        // Greedy refill means it tries to add tokens as soon as possible
        // based on elapsed time
    }

    // ==================== EXAMPLE 4: INTERVAL REFILL ====================

    /**
     * Interval refill: Tokens refill at fixed intervals
     * All tokens added at once when interval completes
     */
    public static void example4_IntervalRefill() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 4: Interval Refill");
        System.out.println("=".repeat(70));

        // Add 10 tokens every 1 second (all at once)
        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofSeconds(1))))
                .build();

        bucket.tryConsume(10);
        System.out.println("After consuming 10: " + bucket.getAvailableTokens());

        // Tokens won't be available until full interval (1 second) passes
    }

    // ==================== EXAMPLE 5: MULTIPLE LIMITS ====================

    /**
     * Multiple rate limits applied simultaneously
     * All limits must be satisfied for consumption to succeed
     */
    public static void example5_MultipleLimits() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 5: Multiple Rate Limits");
        System.out.println("=".repeat(70));

        // Apply 3 different rate limits:
        // - 1000 requests per hour
        // - 100 requests per minute
        // - 10 requests per second
        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.simple(1000, Duration.ofHours(1)))
                .addLimit(Bandwidth.simple(100, Duration.ofMinutes(1)))
                .addLimit(Bandwidth.simple(10, Duration.ofSeconds(1)))
                .build();

        // tryConsume checks ALL limits
        boolean allowed = bucket.tryConsume(5);
        System.out.println("Consume 5 (checks all 3 limits): " + allowed);

        // Try to consume 11 (exceeds 10/second limit)
        allowed = bucket.tryConsume(11);
        System.out.println("Consume 11 (exceeds 10/sec limit): " + allowed);  // false
    }

    // ==================== EXAMPLE 6: CONSUMPTION PROBE ====================

    /**
     * Get detailed information about consumption attempt
     */
    public static void example6_ConsumptionProbe() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 6: Consumption Probe");
        System.out.println("=".repeat(70));

        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.simple(5, Duration.ofSeconds(10)))
                .build();

        bucket.tryConsume(4);  // Use 4 tokens

        // Try to consume 3 (only 1 left)
        var probe = bucket.tryConsumeAndReturnRemaining(3);

        System.out.println("Consumed: " + probe.isConsumed());
        System.out.println("Remaining tokens: " + probe.getRemainingTokens());
        System.out.println("Nanos to wait: " + probe.getNanosToWaitForRefill());
        System.out.println("Wait time in seconds: " +
                probe.getNanosToWaitForRefill() / 1_000_000_000.0);
    }

    // ==================== EXAMPLE 7: ESTIMATION (READ-ONLY) ====================

    /**
     * Estimate if consumption would succeed WITHOUT actually consuming
     */
    public static void example7_Estimation() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 7: Estimation (No Consumption)");
        System.out.println("=".repeat(70));

        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.simple(5, Duration.ofSeconds(1)))
                .build();

        // Estimate WITHOUT consuming
        var estimate = bucket.estimateAbilityToConsume(10);

        System.out.println("Can consume 10? " + estimate.canBeConsumed());
        System.out.println("Current tokens: " + bucket.getAvailableTokens());  // Still 5!

        estimate = bucket.estimateAbilityToConsume(3);
        System.out.println("Can consume 3? " + estimate.canBeConsumed());
    }

    // ==================== EXAMPLE 8: CONSUME AS MUCH AS POSSIBLE ====================

    /**
     * Consume all available tokens
     */
    public static void example8_ConsumeAsMuchAsPossible() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 8: Consume As Much As Possible");
        System.out.println("=".repeat(70));

        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.simple(10, Duration.ofSeconds(1)))
                .build();

        // Consume all available
        long consumed = bucket.tryConsumeAsMuchAsPossible();
        System.out.println("Consumed all: " + consumed);  // 10
        System.out.println("Remaining: " + bucket.getAvailableTokens());  // 0

        // Consume up to limit
        bucket.reset();
        long consumedPartial = bucket.tryConsumeAsMuchAsPossible(3);
        System.out.println("Consumed up to 3: " + consumedPartial);  // 3
        System.out.println("Remaining: " + bucket.getAvailableTokens());  // 7
    }

    // ==================== EXAMPLE 9: ADD TOKENS ====================

    /**
     * Manually add tokens (useful for refunds)
     */
    public static void example9_AddTokens() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 9: Add Tokens");
        System.out.println("=".repeat(70));

        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.simple(5, Duration.ofSeconds(1)))
                .build();

        bucket.tryConsume(5);  // Empty bucket
        System.out.println("After consuming all: " + bucket.getAvailableTokens());

        // Add tokens (capped at capacity)
        bucket.addTokens(10);
        System.out.println("After adding 10 (capped at 5): " + bucket.getAvailableTokens());

        // Force add tokens (can exceed capacity)
        bucket.forceAddTokens(10);
        System.out.println("After force adding 10: " + bucket.getAvailableTokens());  // 15!
    }

    // ==================== EXAMPLE 10: BLOCKING CONSUMPTION ====================

    /**
     * Block until tokens are available
     * WARNING: This blocks the thread!
     */
    public static void example10_BlockingConsumption() throws InterruptedException {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 10: Blocking Consumption");
        System.out.println("=".repeat(70));

        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.simple(2, Duration.ofSeconds(5)))
                .build();

        bucket.tryConsume(2);  // Empty bucket
        System.out.println("Bucket empty, blocking until refill...");

        long startTime = System.currentTimeMillis();

        // This will BLOCK until 1 token is available
        bucket.asBlocking().consume(1);

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("Waited " + elapsed + "ms for refill");
        System.out.println("Successfully consumed 1 token");
    }

    // ==================== EXAMPLE 11: RESET BUCKET ====================

    /**
     * Reset bucket to full capacity
     */
    public static void example11_Reset() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 11: Reset Bucket");
        System.out.println("=".repeat(70));

        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.simple(10, Duration.ofSeconds(1)))
                .build();

        bucket.tryConsume(10);
        System.out.println("After consuming all: " + bucket.getAvailableTokens());

        bucket.reset();
        System.out.println("After reset: " + bucket.getAvailableTokens());  // 10
    }

    // ==================== EXAMPLE 12: REPLACECONFIGURATION ====================

    /**
     * Replace bucket configuration at runtime
     */
    public static void example12_ReplaceConfiguration() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 12: Replace Configuration");
        System.out.println("=".repeat(70));

        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.simple(10, Duration.ofSeconds(1)))
                .build();

        System.out.println("Initial capacity: " + bucket.getAvailableTokens());

        // Create new configuration
        BucketConfiguration newConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(100, Duration.ofSeconds(1)))
                .build();

        bucket.replaceConfiguration(newConfig,
                io.github.bucket4j.TokensInheritanceStrategy.AS_IS);

        System.out.println("After config change: " + bucket.getAvailableTokens());
    }

    // ==================== EXAMPLE 13: CONSUME IGNORING RATE LIMITS ====================

    /**
     * Consume tokens even if not available (goes into debt)
     */
    public static void example13_ConsumeIgnoringLimits() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Example 13: Consume Ignoring Rate Limits");
        System.out.println("=".repeat(70));

        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.simple(5, Duration.ofSeconds(10)))
                .build();

        System.out.println("Initial tokens: " + bucket.getAvailableTokens());

        // Consume 10 tokens (capacity is only 5)
        long penaltyNanos = bucket.consumeIgnoringRateLimits(10);

        System.out.println("Consumed 10 (over limit)");
        System.out.println("Current tokens: " + bucket.getAvailableTokens());  // -5 (debt)
        System.out.println("Penalty time: " + penaltyNanos / 1_000_000_000.0 + " seconds");
    }

    // ==================== MAIN DEMONSTRATION ====================

    public static void main(String[] args) throws InterruptedException {
        System.out.println("\n");
        System.out.println("█".repeat(70));
        System.out.println("██                  REAL BUCKET4J IMPLEMENTATION                  ██");
        System.out.println("█".repeat(70));

        // Run all examples
        example1_BasicBucket();
        example2_BucketConfiguration();
        example3_ClassicRefill();
        example4_IntervalRefill();
        example5_MultipleLimits();
        example6_ConsumptionProbe();
        example7_Estimation();
        example8_ConsumeAsMuchAsPossible();
        example9_AddTokens();
        // example10_BlockingConsumption();  // Uncomment to test (blocks thread)
        example11_Reset();
        example12_ReplaceConfiguration();
        example13_ConsumeIgnoringLimits();

        System.out.println("\n" + "█".repeat(70));
        System.out.println("All examples completed!");
        System.out.println("█".repeat(70) + "\n");
    }
}

