
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Educational implementation of Token Bucket algorithm
 * showing the core data structures and algorithms used in Bucket4j
 */
public class SimpleTokenBucket {

    // ==================== DATA STRUCTURES ====================

    /**
     * Bucket state - the only mutable data
     * In distributed systems, this is stored in Redis
     */
    private static class State {
        final long currentTokens;
        final long lastRefillNanos;

        State(long tokens, long timestamp) {
            this.currentTokens = tokens;
            this.lastRefillNanos = timestamp;
        }

        State withTokens(long newTokens) {
            return new State(newTokens, System.nanoTime());
        }
    }

    /**
     * Immutable configuration
     */
    private final long capacity;
    private final long refillTokens;
    private final long refillPeriodNanos;

    /**
     * Atomic state reference for thread safety (CAS operations)
     */
    private final AtomicReference<State> stateRef;

    // ==================== CONSTRUCTOR ====================

    public SimpleTokenBucket(long capacity, long refillTokens, Duration refillPeriod) {
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriodNanos = refillPeriod.toNanos();

        // Initialize with full capacity
        this.stateRef = new AtomicReference<>(
                new State(capacity, System.nanoTime())
        );
    }

    // ==================== CORE ALGORITHM: REFILL ====================

    /**
     * Calculates new token count based on elapsed time
     * This is the HEART of the Token Bucket algorithm
     *
     * Formula: newTokens = min(currentTokens + elapsed/period * rate, capacity)
     */
    private long calculateRefill(State state, long currentNanos) {
        long elapsedNanos = currentNanos - state.lastRefillNanos;

        if (elapsedNanos <= 0) {
            return state.currentTokens;  // No time passed
        }

        // Calculate tokens generated during elapsed time
        long periodsElapsed = elapsedNanos / refillPeriodNanos;
        long tokensToAdd = periodsElapsed * refillTokens;

        // Add tokens, but cap at capacity
        return Math.min(state.currentTokens + tokensToAdd, capacity);
    }

    // ==================== METHOD 1: tryConsume ====================

    /**
     * Try to consume tokens (non-blocking)
     *
     * Algorithm:
     * 1. Refill based on elapsed time
     * 2. Check if enough tokens
     * 3. If yes: consume and update state
     * 4. If no: reject
     *
     * Time complexity: O(1)
     * Space complexity: O(1)
     */
    public boolean tryConsume(long tokensToConsume) {
        while (true) {  // CAS loop for thread safety
            State current = stateRef.get();
            long now = System.nanoTime();

            // Calculate tokens after refill
            long availableTokens = calculateRefill(current, now);

            // Check if enough tokens
            if (availableTokens < tokensToConsume) {
                return false;  // Not enough tokens
            }

            // Consume tokens
            long newTokens = availableTokens - tokensToConsume;
            State newState = new State(newTokens, now);

            // Atomic update (thread-safe)
            if (stateRef.compareAndSet(current, newState)) {
                return true;  // Success!
            }
            // If CAS failed, another thread modified state - retry
        }
    }

    // ==================== METHOD 2: consumeIgnoringRateLimits ====================

    /**
     * Consume tokens even if not available (goes into debt)
     * Returns penalty time in nanoseconds
     *
     * Algorithm:
     * 1. Always consume tokens (can go negative)
     * 2. If negative, calculate penalty time
     *
     * Example:
     *   State: 2 tokens
     *   Consume: 10 tokens
     *   After: -8 tokens (debt)
     *   Penalty: 8 * (period / rate) nanoseconds
     */
    public long consumeIgnoringRateLimits(long tokens) {
        while (true) {
            State current = stateRef.get();
            long now = System.nanoTime();

            long availableTokens = calculateRefill(current, now);
            long newTokens = availableTokens - tokens;

            State newState = new State(newTokens, now);
            if (stateRef.compareAndSet(current, newState)) {
                if (newTokens >= 0) {
                    return 0;  // No penalty
                } else {
                    // Calculate penalty for debt
                    long deficit = -newTokens;
                    return deficit * refillPeriodNanos / refillTokens;
                }
            }
        }
    }

    // ==================== METHOD 3: tryConsumeAndReturnRemaining ====================

    /**
     * Try to consume and return detailed probe with remaining tokens
     */
    public ConsumptionProbe tryConsumeAndReturnRemaining(long tokensToConsume) {
        while (true) {
            State current = stateRef.get();
            long now = System.nanoTime();

            long availableTokens = calculateRefill(current, now);

            if (availableTokens >= tokensToConsume) {
                // Success case
                long newTokens = availableTokens - tokensToConsume;
                State newState = new State(newTokens, now);

                if (stateRef.compareAndSet(current, newState)) {
                    return new ConsumptionProbe(true, newTokens, 0);
                }
            } else {
                // Failure case - calculate wait time
                long deficit = tokensToConsume - availableTokens;
                long nanosToWait = deficit * refillPeriodNanos / refillTokens;
                return new ConsumptionProbe(false, availableTokens, nanosToWait);
            }
        }
    }

    // ==================== METHOD 4: estimateAbilityToConsume ====================

    /**
     * Check if consumption is possible WITHOUT actually consuming
     * This is a read-only operation - does not modify state
     */
    public EstimationProbe estimateAbilityToConsume(long tokensToConsume) {
        State current = stateRef.get();
        long now = System.nanoTime();

        // Calculate available tokens WITHOUT modifying state
        long availableTokens = calculateRefill(current, now);

        if (availableTokens >= tokensToConsume) {
            return new EstimationProbe(true, availableTokens - tokensToConsume, 0);
        } else {
            long deficit = tokensToConsume - availableTokens;
            long nanosToWait = deficit * refillPeriodNanos / refillTokens;
            return new EstimationProbe(false, availableTokens, nanosToWait);
        }
    }

    // ==================== METHOD 5: tryConsumeAsMuchAsPossible ====================

    /**
     * Consume all available tokens
     * Returns how many tokens were consumed
     */
    public long tryConsumeAsMuchAsPossible() {
        while (true) {
            State current = stateRef.get();
            long now = System.nanoTime();

            long availableTokens = calculateRefill(current, now);

            if (availableTokens == 0) {
                return 0;  // Nothing to consume
            }

            State newState = new State(0, now);
            if (stateRef.compareAndSet(current, newState)) {
                return availableTokens;
            }
        }
    }

    /**
     * Consume up to limit tokens
     * Returns how many were actually consumed
     */
    public long tryConsumeAsMuchAsPossible(long limit) {
        while (true) {
            State current = stateRef.get();
            long now = System.nanoTime();

            long availableTokens = calculateRefill(current, now);
            long toConsume = Math.min(availableTokens, limit);

            if (toConsume == 0) {
                return 0;
            }

            State newState = new State(availableTokens - toConsume, now);
            if (stateRef.compareAndSet(current, newState)) {
                return toConsume;
            }
        }
    }

    // ==================== METHOD 6: addTokens ====================

    /**
     * Manually add tokens (capped at capacity)
     * Useful for refunds or quota resets
     */
    public void addTokens(long tokensToAdd) {
        while (true) {
            State current = stateRef.get();
            long now = System.nanoTime();

            long availableTokens = calculateRefill(current, now);
            long newTokens = Math.min(availableTokens + tokensToAdd, capacity);

            State newState = new State(newTokens, now);
            if (stateRef.compareAndSet(current, newState)) {
                return;
            }
        }
    }

    // ==================== METHOD 7: forceAddTokens ====================

    /**
     * Add tokens WITHOUT capping at capacity (can overflow)
     */
    public void forceAddTokens(long tokensToAdd) {
        while (true) {
            State current = stateRef.get();
            long now = System.nanoTime();

            long availableTokens = calculateRefill(current, now);
            long newTokens = availableTokens + tokensToAdd;  // NO capping!

            State newState = new State(newTokens, now);
            if (stateRef.compareAndSet(current, newState)) {
                return;
            }
        }
    }

    // ==================== METHOD 8: reset ====================

    /**
     * Reset bucket to full capacity
     */
    public void reset() {
        stateRef.set(new State(capacity, System.nanoTime()));
    }

    // ==================== METHOD 9: getAvailableTokens ====================

    /**
     * Get current available tokens (read-only)
     * Does NOT update the refill timestamp
     */
    public long getAvailableTokens() {
        State current = stateRef.get();
        return calculateRefill(current, System.nanoTime());
    }

    // ==================== HELPER CLASSES ====================

    public static class ConsumptionProbe {
        private final boolean consumed;
        private final long remainingTokens;
        private final long nanosToWaitForRefill;

        ConsumptionProbe(boolean consumed, long remainingTokens, long nanosToWait) {
            this.consumed = consumed;
            this.remainingTokens = remainingTokens;
            this.nanosToWaitForRefill = nanosToWait;
        }

        public boolean isConsumed() { return consumed; }
        public long getRemainingTokens() { return remainingTokens; }
        public long getNanosToWaitForRefill() { return nanosToWaitForRefill; }
    }

    public static class EstimationProbe {
        private final boolean canBeConsumed;
        private final long remainingTokens;
        private final long nanosToWaitForRefill;

        EstimationProbe(boolean canBeConsumed, long remainingTokens, long nanosToWait) {
            this.canBeConsumed = canBeConsumed;
            this.remainingTokens = remainingTokens;
            this.nanosToWaitForRefill = nanosToWait;
        }

        public boolean canBeConsumed() { return canBeConsumed; }
        public long getRemainingTokens() { return remainingTokens; }
        public long getNanosToWaitForRefill() { return nanosToWaitForRefill; }
    }

    // ==================== USAGE EXAMPLES ====================

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=".repeat(70));
        System.out.println("Token Bucket Algorithm - Educational Implementation");
        System.out.println("=".repeat(70));

        // Create bucket: 5 tokens, refill 5 tokens every 10 seconds
        SimpleTokenBucket bucket = new SimpleTokenBucket(5, 5, Duration.ofSeconds(10));

        // Example 1: Simple consumption
        System.out.println("\n1. Simple Consumption:");
        System.out.println("   Available: " + bucket.getAvailableTokens());
        System.out.println("   Try consume 3: " + bucket.tryConsume(3));
        System.out.println("   Available: " + bucket.getAvailableTokens());
        System.out.println("   Try consume 3: " + bucket.tryConsume(3));  // Should fail
        System.out.println("   Available: " + bucket.getAvailableTokens());

        // Example 2: Detailed probe
        System.out.println("\n2. Consumption Probe:");
        bucket.reset();
        bucket.tryConsume(4);  // Use 4 tokens
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(3);
        System.out.println("   Consumed: " + probe.isConsumed());
        System.out.println("   Remaining: " + probe.getRemainingTokens());
        System.out.println("   Wait time: " + probe.getNanosToWaitForRefill() / 1_000_000_000.0 + " seconds");

        // Example 3: Estimation (read-only)
        System.out.println("\n3. Estimation (no consumption):");
        bucket.reset();
        EstimationProbe estimate = bucket.estimateAbilityToConsume(10);
        System.out.println("   Can consume 10? " + estimate.canBeConsumed());
        System.out.println("   Still available: " + bucket.getAvailableTokens());  // Unchanged!

        // Example 4: Consume as much as possible
        System.out.println("\n4. Consume As Much As Possible:");
        bucket.reset();
        long consumed = bucket.tryConsumeAsMuchAsPossible(3);
        System.out.println("   Consumed: " + consumed);
        System.out.println("   Remaining: " + bucket.getAvailableTokens());

        // Example 5: Refill over time
        System.out.println("\n5. Refill Over Time:");
        bucket.reset();
        bucket.tryConsume(5);  // Empty bucket
        System.out.println("   T0: Available = " + bucket.getAvailableTokens());
        Thread.sleep(5000);  // Wait 5 seconds (half of refill period)
        System.out.println("   T5: Available = " + bucket.getAvailableTokens());
        Thread.sleep(5000);  // Wait another 5 seconds
        System.out.println("   T10: Available = " + bucket.getAvailableTokens());

        // Example 6: Debt/penalty
        System.out.println("\n6. Ignore Rate Limits (Debt):");
        bucket.reset();
        long penalty = bucket.consumeIgnoringRateLimits(10);
        System.out.println("   Consumed 10 (capacity is 5)");
        System.out.println("   Penalty: " + penalty / 1_000_000_000.0 + " seconds");
        System.out.println("   Current tokens: " + bucket.getAvailableTokens());

        // Example 7: Force add tokens
        System.out.println("\n7. Force Add Tokens (overflow):");
        bucket.reset();
        bucket.forceAddTokens(10);
        System.out.println("   Added 10 tokens (capacity is 5)");
        System.out.println("   Available: " + bucket.getAvailableTokens());  // Will be 15!

        System.out.println("\n" + "=".repeat(70));
    }
}

