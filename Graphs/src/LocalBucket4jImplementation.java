import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Local implementation of Bucket4j structure (NO external dependencies)
 *
 * This mimics the real Bucket4j class hierarchy:
 * - BucketConfiguration (immutable rules)
 * - Bandwidth (rate limit definition)
 * - Refill (refill strategy)
 * - BucketState (mutable runtime data)
 * - Bucket (main API)
 */
public class LocalBucket4jImplementation {

    // ==================== BANDWIDTH (Rate Limit Definition) ====================

    /**
     * Represents a single rate limit bandwidth
     * Example: 10 tokens per 1 second
     */
    public static class Bandwidth {
        private final long capacity;
        private final Refill refill;

        private Bandwidth(long capacity, Refill refill) {
            this.capacity = capacity;
            this.refill = refill;
        }

        /**
         * Simple bandwidth: capacity = refill amount
         * Example: simple(10, Duration.ofSeconds(1)) = 10 tokens per second
         */
        public static Bandwidth simple(long capacity, Duration period) {
            return new Bandwidth(capacity, Refill.greedy(capacity, period));
        }

        /**
         * Classic bandwidth: separate capacity and refill
         * Example: classic(100, Refill.greedy(10, Duration.ofSeconds(1)))
         *   = bucket holds max 100 tokens, refills 10 tokens per second
         */
        public static Bandwidth classic(long capacity, Refill refill) {
            return new Bandwidth(capacity, refill);
        }

        public long getCapacity() { return capacity; }
        public Refill getRefill() { return refill; }
    }

    // ==================== REFILL (Refill Strategy) ====================

    /**
     * Defines how tokens are refilled
     */
    public static class Refill {
        private final long tokens;
        private final long periodNanos;
        private final RefillStrategy strategy;

        private enum RefillStrategy {
            GREEDY,      // Refill tokens continuously based on time elapsed
            INTERVALLY   // Refill all tokens at once when period completes
        }

        private Refill(long tokens, long periodNanos, RefillStrategy strategy) {
            this.tokens = tokens;
            this.periodNanos = periodNanos;
            this.strategy = strategy;
        }

        /**
         * Greedy refill: Add tokens continuously based on elapsed time
         * Most common strategy
         */
        public static Refill greedy(long tokens, Duration period) {
            return new Refill(tokens, period.toNanos(), RefillStrategy.GREEDY);
        }

        /**
         * Interval refill: Add all tokens at once when period completes
         * Example: 10 tokens every 1 second = all 10 added at second boundary
         */
        public static Refill intervally(long tokens, Duration period) {
            return new Refill(tokens, period.toNanos(), RefillStrategy.INTERVALLY);
        }

        public long getTokens() { return tokens; }
        public long getPeriodNanos() { return periodNanos; }
        public boolean isGreedy() { return strategy == RefillStrategy.GREEDY; }
    }

    // ==================== BUCKET CONFIGURATION ====================

    /**
     * Immutable configuration defining rate limit rules
     * Can be reused for multiple buckets
     */
    public static class BucketConfiguration {
        private final List<Bandwidth> bandwidths;

        private BucketConfiguration(List<Bandwidth> bandwidths) {
            this.bandwidths = new ArrayList<>(bandwidths);
        }

        public static Builder builder() {
            return new Builder();
        }

        public List<Bandwidth> getBandwidths() {
            return new ArrayList<>(bandwidths);
        }

        public static class Builder {
            private final List<Bandwidth> bandwidths = new ArrayList<>();

            public Builder addLimit(Bandwidth bandwidth) {
                bandwidths.add(bandwidth);
                return this;
            }

            public BucketConfiguration build() {
                if (bandwidths.isEmpty()) {
                    throw new IllegalStateException("At least one bandwidth must be configured");
                }
                return new BucketConfiguration(bandwidths);
            }
        }
    }

    // ==================== BUCKET STATE ====================

    /**
     * Mutable runtime state of the bucket
     * Stores current tokens and last refill time for each bandwidth
     */
    public static class BucketState {
        private final long[] stateData;

        /**
         * State array structure:
         * For each bandwidth:
         *   [i*2]   = current tokens
         *   [i*2+1] = last refill timestamp (nanos)
         */
        private BucketState(long[] stateData) {
            this.stateData = stateData.clone();
        }

        /**
         * Create initial state from configuration
         */
        public static BucketState create(BucketConfiguration config, long currentTimeNanos) {
            int bandwidthCount = config.getBandwidths().size();
            long[] stateData = new long[bandwidthCount * 2];

            for (int i = 0; i < bandwidthCount; i++) {
                Bandwidth bandwidth = config.getBandwidths().get(i);
                stateData[i * 2] = bandwidth.getCapacity();      // Initial tokens = capacity
                stateData[i * 2 + 1] = currentTimeNanos;         // Initial timestamp
            }

            return new BucketState(stateData);
        }

        public long getCurrentTokens(int bandwidthIndex) {
            return stateData[bandwidthIndex * 2];
        }

        public long getLastRefillTime(int bandwidthIndex) {
            return stateData[bandwidthIndex * 2 + 1];
        }

        public BucketState withTokens(int bandwidthIndex, long tokens, long timestamp) {
            long[] newStateData = stateData.clone();
            newStateData[bandwidthIndex * 2] = tokens;
            newStateData[bandwidthIndex * 2 + 1] = timestamp;
            return new BucketState(newStateData);
        }

        public BucketState copy() {
            return new BucketState(stateData);
        }

        public long[] getStateData() {
            return stateData.clone();
        }
    }

    // ==================== CONSUMPTION PROBE ====================

    /**
     * Result of a consumption attempt with detailed information
     */
    public static class ConsumptionProbe {
        private final boolean consumed;
        private final long remainingTokens;
        private final long nanosToWaitForRefill;

        public ConsumptionProbe(boolean consumed, long remainingTokens, long nanosToWaitForRefill) {
            this.consumed = consumed;
            this.remainingTokens = remainingTokens;
            this.nanosToWaitForRefill = nanosToWaitForRefill;
        }

        public boolean isConsumed() { return consumed; }
        public long getRemainingTokens() { return remainingTokens; }
        public long getNanosToWaitForRefill() { return nanosToWaitForRefill; }
    }

    // ==================== BUCKET (Main API) ====================

    /**
     * The main rate limiter bucket
     * Thread-safe using AtomicReference + CAS
     */
    public static class Bucket {
        private final BucketConfiguration configuration;
        private final AtomicReference<BucketState> stateRef;

        private Bucket(BucketConfiguration configuration) {
            this.configuration = configuration;
            long now = System.nanoTime();
            this.stateRef = new AtomicReference<>(BucketState.create(configuration, now));
        }

        public static Builder builder() {
            return new Builder();
        }

        /**
         * Try to consume tokens (returns true/false)
         */
        public boolean tryConsume(long tokens) {
            while (true) {
                BucketState current = stateRef.get();
                long now = System.nanoTime();

                BucketState refilled = refillAll(current, now);

                // Check if all bandwidths have enough tokens
                long minAvailable = Long.MAX_VALUE;
                for (int i = 0; i < configuration.getBandwidths().size(); i++) {
                    long available = refilled.getCurrentTokens(i);
                    minAvailable = Math.min(minAvailable, available);
                }

                if (minAvailable < tokens) {
                    return false;  // Not enough tokens
                }

                // Consume from all bandwidths
                BucketState newState = consumeFromAll(refilled, tokens, now);

                if (stateRef.compareAndSet(current, newState)) {
                    return true;
                }
                // CAS failed, retry
            }
        }

        /**
         * Try to consume and return detailed probe
         */
        public ConsumptionProbe tryConsumeAndReturnRemaining(long tokens) {
            while (true) {
                BucketState current = stateRef.get();
                long now = System.nanoTime();

                BucketState refilled = refillAll(current, now);

                // Check all bandwidths
                long minAvailable = Long.MAX_VALUE;
                int limitingBandwidthIndex = 0;

                for (int i = 0; i < configuration.getBandwidths().size(); i++) {
                    long available = refilled.getCurrentTokens(i);
                    if (available < minAvailable) {
                        minAvailable = available;
                        limitingBandwidthIndex = i;
                    }
                }

                if (minAvailable < tokens) {
                    // Calculate wait time
                    Bandwidth limitingBandwidth = configuration.getBandwidths().get(limitingBandwidthIndex);
                    long deficit = tokens - minAvailable;
                    long nanosToWait = calculateWaitTime(deficit, limitingBandwidth);

                    return new ConsumptionProbe(false, minAvailable, nanosToWait);
                }

                // Consume from all bandwidths
                BucketState newState = consumeFromAll(refilled, tokens, now);

                if (stateRef.compareAndSet(current, newState)) {
                    long remaining = Long.MAX_VALUE;
                    for (int i = 0; i < configuration.getBandwidths().size(); i++) {
                        remaining = Math.min(remaining, newState.getCurrentTokens(i));
                    }
                    return new ConsumptionProbe(true, remaining, 0);
                }
            }
        }

        /**
         * Get available tokens (minimum across all bandwidths)
         */
        public long getAvailableTokens() {
            BucketState current = stateRef.get();
            long now = System.nanoTime();
            BucketState refilled = refillAll(current, now);

            long minAvailable = Long.MAX_VALUE;
            for (int i = 0; i < configuration.getBandwidths().size(); i++) {
                minAvailable = Math.min(minAvailable, refilled.getCurrentTokens(i));
            }
            return minAvailable;
        }

        /**
         * Add tokens to bucket
         */
        public void addTokens(long tokens) {
            while (true) {
                BucketState current = stateRef.get();
                long now = System.nanoTime();

                BucketState refilled = refillAll(current, now);
                BucketState newState = refilled;

                for (int i = 0; i < configuration.getBandwidths().size(); i++) {
                    Bandwidth bandwidth = configuration.getBandwidths().get(i);
                    long currentTokens = newState.getCurrentTokens(i);
                    long newTokens = Math.min(currentTokens + tokens, bandwidth.getCapacity());
                    newState = newState.withTokens(i, newTokens, now);
                }

                if (stateRef.compareAndSet(current, newState)) {
                    return;
                }
            }
        }

        /**
         * Reset bucket to full capacity
         */
        public void reset() {
            long now = System.nanoTime();
            stateRef.set(BucketState.create(configuration, now));
        }

        /**
         * Consume tokens ignoring rate limits (can go negative)
         */
        public long consumeIgnoringRateLimits(long tokens) {
            while (true) {
                BucketState current = stateRef.get();
                long now = System.nanoTime();

                BucketState refilled = refillAll(current, now);
                BucketState newState = consumeFromAll(refilled, tokens, now);

                if (stateRef.compareAndSet(current, newState)) {
                    // Calculate penalty time
                    long maxDeficit = 0;
                    int deficitBandwidthIndex = 0;

                    for (int i = 0; i < configuration.getBandwidths().size(); i++) {
                        long currentTokens = newState.getCurrentTokens(i);
                        if (currentTokens < 0 && -currentTokens > maxDeficit) {
                            maxDeficit = -currentTokens;
                            deficitBandwidthIndex = i;
                        }
                    }

                    if (maxDeficit > 0) {
                        Bandwidth bandwidth = configuration.getBandwidths().get(deficitBandwidthIndex);
                        return calculateWaitTime(maxDeficit, bandwidth);
                    }
                    return 0;
                }
            }
        }

        /**
         * Consume as much as possible
         */
        public long tryConsumeAsMuchAsPossible() {
            while (true) {
                BucketState current = stateRef.get();
                long now = System.nanoTime();

                BucketState refilled = refillAll(current, now);

                // Find minimum available
                long minAvailable = Long.MAX_VALUE;
                for (int i = 0; i < configuration.getBandwidths().size(); i++) {
                    minAvailable = Math.min(minAvailable, refilled.getCurrentTokens(i));
                }

                if (minAvailable <= 0) {
                    return 0;
                }

                BucketState newState = consumeFromAll(refilled, minAvailable, now);

                if (stateRef.compareAndSet(current, newState)) {
                    return minAvailable;
                }
            }
        }

        // ==================== HELPER METHODS ====================

        private BucketState refillAll(BucketState state, long now) {
            BucketState result = state;

            for (int i = 0; i < configuration.getBandwidths().size(); i++) {
                Bandwidth bandwidth = configuration.getBandwidths().get(i);
                long currentTokens = state.getCurrentTokens(i);
                long lastRefill = state.getLastRefillTime(i);

                long newTokens = calculateRefill(
                    currentTokens,
                    lastRefill,
                    now,
                    bandwidth.getCapacity(),
                    bandwidth.getRefill()
                );

                result = result.withTokens(i, newTokens, now);
            }

            return result;
        }

        private long calculateRefill(long currentTokens, long lastRefill, long now,
                                     long capacity, Refill refill) {
            if (currentTokens >= capacity) {
                return capacity;
            }

            long elapsed = now - lastRefill;
            if (elapsed <= 0) {
                return currentTokens;
            }

            if (refill.isGreedy()) {
                // Greedy: Add tokens proportional to elapsed time
                long periodsElapsed = elapsed / refill.getPeriodNanos();
                long tokensToAdd = periodsElapsed * refill.getTokens();
                return Math.min(currentTokens + tokensToAdd, capacity);
            } else {
                // Intervally: Add all tokens if full period elapsed
                if (elapsed >= refill.getPeriodNanos()) {
                    return Math.min(currentTokens + refill.getTokens(), capacity);
                }
                return currentTokens;
            }
        }

        private BucketState consumeFromAll(BucketState state, long tokens, long now) {
            BucketState result = state;

            for (int i = 0; i < configuration.getBandwidths().size(); i++) {
                long currentTokens = state.getCurrentTokens(i);
                long newTokens = currentTokens - tokens;
                result = result.withTokens(i, newTokens, now);
            }

            return result;
        }

        private long calculateWaitTime(long deficit, Bandwidth bandwidth) {
            Refill refill = bandwidth.getRefill();
            long periodsNeeded = (deficit + refill.getTokens() - 1) / refill.getTokens();
            return periodsNeeded * refill.getPeriodNanos();
        }

        // ==================== BUILDER ====================

        public static class Builder {
            private final BucketConfiguration.Builder configBuilder = BucketConfiguration.builder();

            public Builder addLimit(Bandwidth bandwidth) {
                configBuilder.addLimit(bandwidth);
                return this;
            }

            public Bucket build() {
                return new Bucket(configBuilder.build());
            }
        }
    }

    // ==================== USAGE EXAMPLES ====================

    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("LOCAL BUCKET4J IMPLEMENTATION (No External Dependencies)");
        System.out.println("=".repeat(70));

        example1_BasicBucket();
        example2_MultipleLimits();
        example3_ConsumptionProbe();
        example4_ReuseConfiguration();
        example5_AddTokens();
        example6_ConsumeIgnoringLimits();
    }

    static void example1_BasicBucket() {
        System.out.println("\n--- Example 1: Basic Bucket ---");

        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.simple(10, Duration.ofSeconds(1)))
                .build();

        System.out.println("Available: " + bucket.getAvailableTokens());
        System.out.println("Consume 3: " + bucket.tryConsume(3));
        System.out.println("Remaining: " + bucket.getAvailableTokens());
        System.out.println("Consume 10: " + bucket.tryConsume(10));  // false
    }

    static void example2_MultipleLimits() {
        System.out.println("\n--- Example 2: Multiple Limits ---");

        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.simple(100, Duration.ofHours(1)))
                .addLimit(Bandwidth.simple(10, Duration.ofSeconds(1)))
                .build();

        System.out.println("Available: " + bucket.getAvailableTokens());  // Min of both
        System.out.println("Consume 5: " + bucket.tryConsume(5));
        System.out.println("Consume 10: " + bucket.tryConsume(10));  // false (exceeds 10/sec)
    }

    static void example3_ConsumptionProbe() {
        System.out.println("\n--- Example 3: Consumption Probe ---");

        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.simple(5, Duration.ofSeconds(10)))
                .build();

        bucket.tryConsume(4);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(3);
        System.out.println("Consumed: " + probe.isConsumed());
        System.out.println("Remaining: " + probe.getRemainingTokens());
        System.out.println("Wait time: " + probe.getNanosToWaitForRefill() / 1_000_000_000.0 + "s");
    }

    static void example4_ReuseConfiguration() {
        System.out.println("\n--- Example 4: Reuse Configuration ---");

        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(100, Duration.ofMinutes(1)))
                .build();

        // Same config for multiple users (in real app, you'd store state separately)
        Bucket user1 = Bucket.builder()
                .addLimit(Bandwidth.simple(100, Duration.ofMinutes(1)))
                .build();

        Bucket user2 = Bucket.builder()
                .addLimit(Bandwidth.simple(100, Duration.ofMinutes(1)))
                .build();

        user1.tryConsume(10);
        user2.tryConsume(20);

        System.out.println("User 1: " + user1.getAvailableTokens());
        System.out.println("User 2: " + user2.getAvailableTokens());
    }

    static void example5_AddTokens() {
        System.out.println("\n--- Example 5: Add Tokens ---");

        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.simple(10, Duration.ofSeconds(1)))
                .build();

        bucket.tryConsume(10);
        System.out.println("After consuming all: " + bucket.getAvailableTokens());

        bucket.addTokens(5);
        System.out.println("After adding 5: " + bucket.getAvailableTokens());
    }

    static void example6_ConsumeIgnoringLimits() {
        System.out.println("\n--- Example 6: Consume Ignoring Limits ---");

        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.simple(5, Duration.ofSeconds(10)))
                .build();

        long penalty = bucket.consumeIgnoringRateLimits(10);
        System.out.println("Consumed 10 (over limit of 5)");
        System.out.println("Current tokens: " + bucket.getAvailableTokens());
        System.out.println("Penalty: " + penalty / 1_000_000_000.0 + "s");
    }
}

