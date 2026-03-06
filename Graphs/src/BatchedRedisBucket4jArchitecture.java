import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ============================================================================
 * BUCKET4J DISTRIBUTED ARCHITECTURE WITH BATCHED REDIS UPDATES
 * ============================================================================
 *
 * PROBLEM:
 * - Every request hits Redis (high latency, high Redis load)
 * - In high-traffic systems, Redis becomes bottleneck
 *
 * SOLUTION:
 * - Keep local cache in each K8s pod
 * - Batch updates to Redis (flush every 10% token consumption)
 * - Use consistent hashing to route tenants to same pods
 * - Reduces Redis calls by 90%!
 *
 * ARCHITECTURE:
 *
 *     Internet
 *        ↓
 *   Load Balancer (K8s Ingress)
 *        ↓
 *   K8s Service (Consistent Hashing by tenant_id)
 *        ↓
 *   ┌─────────────────────────────────────────────────────┐
 *   │              K8s Cluster                            │
 *   │                                                     │
 *   │  Pod-1           Pod-2           Pod-3             │
 *   │  ┌────────┐      ┌────────┐      ┌────────┐       │
 *   │  │ Local  │      │ Local  │      │ Local  │       │
 *   │  │ Cache  │      │ Cache  │      │ Cache  │       │
 *   │  │ (hot)  │      │ (hot)  │      │ (hot)  │       │
 *   │  └───┬────┘      └───┬────┘      └───┬────┘       │
 *   │      │               │               │             │
 *   │      └───────────────┼───────────────┘             │
 *   │           Batch flush every 10%                    │
 *   └──────────────────────┼──────────────────────────────┘
 *                          ↓
 *                     Redis Cluster
 *                   (source of truth)
 *
 * KEY CONCEPTS:
 * 1. Consistent Hashing: Same tenant_id → Same pod
 * 2. Local Cache: Fast, in-memory token tracking
 * 3. Batched Writes: Only flush to Redis at thresholds
 * 4. Eventual Consistency: Accept slight drift for performance
 */
public class BatchedRedisBucket4jArchitecture {

    // ========================================================================
    // PART 1: CONSISTENT HASHING CONFIGURATION
    // ========================================================================

    /**
     * K8S SERVICE CONFIGURATION
     * ════════════════════════════════════════════════════════════════════
     *
     * File: k8s-service.yaml
     *
     * apiVersion: v1
     * kind: Service
     * metadata:
     *   name: rate-limiter-service
     *   annotations:
     *     # Enable session affinity based on client IP or header
     *     service.kubernetes.io/topology-aware-hints: "auto"
     * spec:
     *   selector:
     *     app: rate-limiter
     *   ports:
     *     - protocol: TCP
     *       port: 8080
     *       targetPort: 8080
     *   # Consistent hashing via session affinity
     *   sessionAffinity: ClientIP
     *   sessionAffinityConfig:
     *     clientIP:
     *       timeoutSeconds: 3600  # 1 hour sticky session
     *
     * ---
     *
     * NGINX INGRESS CONFIGURATION (Better option)
     * ════════════════════════════════════════════════════════════════════
     *
     * File: ingress.yaml
     *
     * apiVersion: networking.k8s.io/v1
     * kind: Ingress
     * metadata:
     *   name: rate-limiter-ingress
     *   annotations:
     *     # Consistent hashing by tenant_id header
     *     nginx.ingress.kubernetes.io/upstream-hash-by: "$http_x_tenant_id"
     *     # OR by cookie
     *     # nginx.ingress.kubernetes.io/upstream-hash-by: "$cookie_tenant_id"
     * spec:
     *   ingressClassName: nginx
     *   rules:
     *     - host: api.example.com
     *       http:
     *         paths:
     *           - path: /
     *             pathType: Prefix
     *             backend:
     *               service:
     *                 name: rate-limiter-service
     *                 port:
     *                   number: 8080
     *
     * HOW IT WORKS:
     * - Request with header "X-Tenant-ID: tenant123"
     * - NGINX calculates: hash(tenant123) % pod_count
     * - Always routes tenant123 to same pod (unless pod dies)
     * - Local cache hits are maximized!
     */

    // ========================================================================
    // PART 2: BATCHED BUCKET IMPLEMENTATION
    // ========================================================================

    /**
     * Hybrid bucket: Local cache + Batched Redis sync
     */
    public static class BatchedBucket {

        // Configuration
        private final String tenantId;
        private final long capacity;
        private final long refillTokens;
        private final long refillPeriodNanos;

        // Local state (fast, in-memory)
        private final AtomicReference<LocalState> localStateRef;

        // Sync tracking
        private final AtomicLong tokensConsumedSinceLastSync;
        private final double flushThresholdPercent = 0.10;  // Flush at 10% consumption

        // Redis connection (injected)
        private final RedisClient redisClient;

        // Background sync
        private final ScheduledExecutorService syncScheduler;

        static class LocalState {
            final long currentTokens;
            final long lastRefillNanos;
            final long lastSyncNanos;

            LocalState(long tokens, long refillTime, long syncTime) {
                this.currentTokens = tokens;
                this.lastRefillNanos = refillTime;
                this.lastSyncNanos = syncTime;
            }
        }

        public BatchedBucket(String tenantId, long capacity, long refillTokens,
                            long refillPeriodNanos, RedisClient redisClient) {
            this.tenantId = tenantId;
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillPeriodNanos = refillPeriodNanos;
            this.redisClient = redisClient;
            this.tokensConsumedSinceLastSync = new AtomicLong(0);

            // Load initial state from Redis
            long now = System.nanoTime();
            RemoteState remoteState = redisClient.loadState(tenantId);
            if (remoteState != null) {
                this.localStateRef = new AtomicReference<>(
                    new LocalState(remoteState.tokens, remoteState.lastRefill, now)
                );
            } else {
                this.localStateRef = new AtomicReference<>(
                    new LocalState(capacity, now, now)
                );
            }

            // Periodic sync every 5 seconds (safety net)
            this.syncScheduler = Executors.newSingleThreadScheduledExecutor();
            this.syncScheduler.scheduleAtFixedRate(
                this::periodicSync, 5, 5, TimeUnit.SECONDS
            );
        }

        /**
         * Try to consume tokens (uses local cache)
         */
        public boolean tryConsume(long tokens) {
            while (true) {
                LocalState current = localStateRef.get();
                long now = System.nanoTime();

                // Calculate refill (local)
                long availableTokens = calculateRefill(current, now);

                if (availableTokens < tokens) {
                    return false;  // Not enough tokens
                }

                // Update local state
                long newTokens = availableTokens - tokens;
                LocalState newState = new LocalState(newTokens, now, current.lastSyncNanos);

                if (localStateRef.compareAndSet(current, newState)) {
                    // Track consumption
                    long consumed = tokensConsumedSinceLastSync.addAndGet(tokens);

                    // Check if we need to flush to Redis
                    long flushThreshold = (long)(capacity * flushThresholdPercent);
                    if (consumed >= flushThreshold) {
                        flushToRedis(newState, now);
                    }

                    return true;
                }
            }
        }

        /**
         * Flush local state to Redis (batched write)
         */
        private void flushToRedis(LocalState state, long now) {
            try {
                // Atomic flush
                if (tokensConsumedSinceLastSync.compareAndSet(
                    tokensConsumedSinceLastSync.get(), 0)) {

                    // Write to Redis
                    redisClient.saveState(tenantId,
                        new RemoteState(state.currentTokens, state.lastRefillNanos));

                    // Update last sync time
                    LocalState current = localStateRef.get();
                    LocalState synced = new LocalState(
                        current.currentTokens,
                        current.lastRefillNanos,
                        now
                    );
                    localStateRef.compareAndSet(current, synced);

                    System.out.println("[FLUSH] Tenant: " + tenantId +
                        " | Tokens: " + state.currentTokens + " → Redis");
                }
            } catch (Exception e) {
                System.err.println("Failed to flush to Redis: " + e.getMessage());
                // Continue using local cache on Redis failure
            }
        }

        /**
         * Periodic sync (safety net)
         */
        private void periodicSync() {
            LocalState current = localStateRef.get();
            long now = System.nanoTime();

            // Only sync if we have consumed tokens since last sync
            if (tokensConsumedSinceLastSync.get() > 0) {
                flushToRedis(current, now);
            }
        }

        private long calculateRefill(LocalState state, long now) {
            long elapsed = now - state.lastRefillNanos;
            if (elapsed <= 0) return state.currentTokens;

            long periodsElapsed = elapsed / refillPeriodNanos;
            long tokensToAdd = periodsElapsed * refillTokens;
            return Math.min(state.currentTokens + tokensToAdd, capacity);
        }

        public void shutdown() {
            syncScheduler.shutdown();
            // Final flush
            flushToRedis(localStateRef.get(), System.nanoTime());
        }
    }

    // ========================================================================
    // PART 3: REDIS CLIENT (Simplified Interface)
    // ========================================================================

    static class RemoteState {
        long tokens;
        long lastRefill;

        RemoteState(long tokens, long lastRefill) {
            this.tokens = tokens;
            this.lastRefill = lastRefill;
        }
    }

    interface RedisClient {
        RemoteState loadState(String tenantId);
        void saveState(String tenantId, RemoteState state);
    }

    /**
     * Mock Redis client for demonstration
     */
    static class MockRedisClient implements RedisClient {
        private final Map<String, RemoteState> storage = new ConcurrentHashMap<>();
        private final AtomicLong redisCallCount = new AtomicLong(0);

        @Override
        public RemoteState loadState(String tenantId) {
            redisCallCount.incrementAndGet();
            System.out.println("[REDIS READ] Tenant: " + tenantId +
                " | Total calls: " + redisCallCount.get());
            return storage.get(tenantId);
        }

        @Override
        public void saveState(String tenantId, RemoteState state) {
            redisCallCount.incrementAndGet();
            storage.put(tenantId, state);
            System.out.println("[REDIS WRITE] Tenant: " + tenantId +
                " | Tokens: " + state.tokens +
                " | Total calls: " + redisCallCount.get());
        }

        public long getCallCount() {
            return redisCallCount.get();
        }
    }

    // ========================================================================
    // PART 4: BUCKET MANAGER (Per-Pod Cache)
    // ========================================================================

    /**
     * Manages buckets for all tenants in this pod
     */
    public static class BucketManager {
        private final Map<String, BatchedBucket> buckets = new ConcurrentHashMap<>();
        private final RedisClient redisClient;

        // Default configuration
        private final long capacity = 100;
        private final long refillTokens = 100;
        private final long refillPeriodNanos = Duration.ofMinutes(1).toNanos();

        public BucketManager(RedisClient redisClient) {
            this.redisClient = redisClient;
        }

        /**
         * Get or create bucket for tenant
         */
        public BatchedBucket getBucket(String tenantId) {
            return buckets.computeIfAbsent(tenantId, id ->
                new BatchedBucket(id, capacity, refillTokens, refillPeriodNanos, redisClient)
            );
        }

        /**
         * Check rate limit for tenant
         */
        public boolean allowRequest(String tenantId, long tokens) {
            BatchedBucket bucket = getBucket(tenantId);
            return bucket.tryConsume(tokens);
        }

        public void shutdown() {
            buckets.values().forEach(BatchedBucket::shutdown);
        }
    }

    // ========================================================================
    // PART 5: COMPLETE K8S DEPLOYMENT
    // ========================================================================

    /**
     * DEPLOYMENT YAML
     * ════════════════════════════════════════════════════════════════════
     *
     * File: deployment.yaml
     *
     * apiVersion: apps/v1
     * kind: Deployment
     * metadata:
     *   name: rate-limiter
     * spec:
     *   replicas: 3  # 3 pods for HA
     *   selector:
     *     matchLabels:
     *       app: rate-limiter
     *   template:
     *     metadata:
     *       labels:
     *         app: rate-limiter
     *     spec:
     *       containers:
     *         - name: rate-limiter
     *           image: your-repo/rate-limiter:latest
     *           ports:
     *             - containerPort: 8080
     *           env:
     *             - name: REDIS_HOST
     *               value: "redis-cluster.default.svc.cluster.local"
     *             - name: REDIS_PORT
     *               value: "6379"
     *             - name: FLUSH_THRESHOLD_PERCENT
     *               value: "0.10"  # 10%
     *             - name: POD_NAME
     *               valueFrom:
     *                 fieldRef:
     *                   fieldPath: metadata.name
     *           resources:
     *             requests:
     *               memory: "256Mi"
     *               cpu: "250m"
     *             limits:
     *               memory: "512Mi"
     *               cpu: "500m"
     *           livenessProbe:
     *             httpGet:
     *               path: /health
     *               port: 8080
     *             initialDelaySeconds: 30
     *             periodSeconds: 10
     *           readinessProbe:
     *             httpGet:
     *               path: /ready
     *               port: 8080
     *             initialDelaySeconds: 5
     *             periodSeconds: 5
     *
     * ---
     *
     * HORIZONTAL POD AUTOSCALER
     * ════════════════════════════════════════════════════════════════════
     *
     * File: hpa.yaml
     *
     * apiVersion: autoscaling/v2
     * kind: HorizontalPodAutoscaler
     * metadata:
     *   name: rate-limiter-hpa
     * spec:
     *   scaleTargetRef:
     *     apiVersion: apps/v1
     *     kind: Deployment
     *     name: rate-limiter
     *   minReplicas: 3
     *   maxReplicas: 10
     *   metrics:
     *     - type: Resource
     *       resource:
     *         name: cpu
     *         target:
     *           type: Utilization
     *           averageUtilization: 70
     *     - type: Resource
     *       resource:
     *         name: memory
     *         target:
     *           type: Utilization
     *           averageUtilization: 80
     *
     * PROBLEM WITH AUTOSCALING + CONSISTENT HASHING:
     * - When pods scale up/down, hash ring changes
     * - Some tenants get routed to different pods
     * - Local cache miss → Load from Redis
     * - Solution: Use consistent hashing with virtual nodes
     */

    // ========================================================================
    // PART 6: REQUEST FLOW DIAGRAM
    // ========================================================================

    /**
     * DETAILED REQUEST FLOW
     * ════════════════════════════════════════════════════════════════════
     *
     * Request 1-9 (Local cache hits):
     * ─────────────────────────────────────────────────────────────────────
     *
     * Client                 K8s Pod                    Redis
     *   │                       │                         │
     *   ├─ GET /api (tenant1) ─→│                         │
     *   │                       ├─ tryConsume(1)          │
     *   │                       │  Local: 100 → 99        │
     *   │                       │  Consumed: 1            │
     *   │←────── 200 OK ────────┤                         │
     *   │                       │                         │
     *   ├─ GET /api (tenant1) ─→│                         │
     *   │                       ├─ tryConsume(1)          │
     *   │                       │  Local: 99 → 98         │
     *   │                       │  Consumed: 2            │
     *   │←────── 200 OK ────────┤                         │
     *   │                       │                         │
     *   ... (Requests 3-9) ...  │                         │
     *   │                       │  Consumed: 9            │
     *   │                       │  (No Redis calls!)      │
     *
     *
     * Request 10 (Triggers flush at 10%):
     * ─────────────────────────────────────────────────────────────────────
     *
     * Client                 K8s Pod                    Redis
     *   │                       │                         │
     *   ├─ GET /api (tenant1) ─→│                         │
     *   │                       ├─ tryConsume(1)          │
     *   │                       │  Local: 91 → 90         │
     *   │                       │  Consumed: 10 ≥ 10%     │
     *   │                       │                         │
     *   │                       ├─ flushToRedis() ───────→│
     *   │                       │                    HSET bucket:tenant1
     *   │                       │                    tokens=90
     *   │                       │←───────── OK ───────────┤
     *   │                       │  Reset consumed: 0      │
     *   │←────── 200 OK ────────┤                         │
     *
     *
     * BENEFIT: 90% reduction in Redis calls!
     * - Without batching: 100 requests = 100 Redis calls
     * - With batching (10%): 100 requests = 10 Redis calls
     */

    // ========================================================================
    // PART 7: EDGE CASES & SOLUTIONS
    // ========================================================================

    /**
     * EDGE CASE 1: Pod Restart/Crash
     * ════════════════════════════════════════════════════════════════════
     *
     * Problem:
     *   Pod crashes with local state: tokens=90, consumed since last sync=5
     *   Redis has old state: tokens=95
     *   Lost 5 tokens!
     *
     * Solution 1: Periodic sync (implemented above)
     *   - Flush to Redis every 5 seconds
     *   - Max loss = 5 seconds of traffic
     *
     * Solution 2: Graceful shutdown
     *   - PreStop hook in K8s
     *   - Flush on SIGTERM
     *
     * File: deployment.yaml (add lifecycle hook)
     *
     * lifecycle:
     *   preStop:
     *     exec:
     *       command: ["/bin/sh", "-c", "curl -X POST http://localhost:8080/flush"]
     *
     *
     * EDGE CASE 2: Consistent Hashing Rehash
     * ════════════════════════════════════════════════════════════════════
     *
     * Problem:
     *   Pod-2 dies → tenant1 now routes to Pod-3
     *   Pod-3 doesn't have local cache for tenant1
     *
     * Solution:
     *   1. Load from Redis (cold start)
     *   2. Continue with local cache
     *   3. Use virtual nodes in consistent hashing (minimize rehashing)
     *
     * Flow:
     *   Request → Pod-3 (new)
     *     ├─ Check local cache → MISS
     *     ├─ Load from Redis → GET bucket:tenant1 (tokens=90)
     *     ├─ Create local cache → tokens=90
     *     └─ Continue normal operation
     *
     *
     * EDGE CASE 3: Clock Skew Between Pods
     * ════════════════════════════════════════════════════════════════════
     *
     * Problem:
     *   Pod-1 time: 10:00:00
     *   Pod-2 time: 10:00:05 (5 seconds ahead)
     *   Tenant switches pods → incorrect refill calculation
     *
     * Solution:
     *   - Use NTP in K8s cluster
     *   - Store timestamps in Redis, not just in memory
     *   - Use monotonic time (System.nanoTime()) within same pod
     *   - Use wall clock (System.currentTimeMillis()) for Redis sync
     *
     *
     * EDGE CASE 4: Redis Failure
     * ════════════════════════════════════════════════════════════════════
     *
     * Problem:
     *   Redis is down → Can't load/save state
     *
     * Solution:
     *   - Continue with local cache (degraded mode)
     *   - Each pod enforces rate limit independently
     *   - Log errors, retry with exponential backoff
     *   - Alert operations team
     *
     * Trade-off:
     *   - Without Redis: Tenant can consume N * capacity (N = pod count)
     *   - Example: 3 pods, capacity=100 → max 300 requests
     *   - Better than no rate limiting at all!
     *
     *
     * EDGE CASE 5: High Consumption Burst
     * ════════════════════════════════════════════════════════════════════
     *
     * Problem:
     *   Tenant consumes 50 tokens instantly
     *   Local cache: 100 → 50
     *   Not flushed yet (only at 10% = 10 tokens)
     *   Pod crashes → Redis has old state (100 tokens)
     *
     * Solution:
     *   - Flush on large consumption (e.g., >25% of capacity)
     *   - Progressive thresholds: 10%, 25%, 50%
     *
     * Code modification:
     *   if (consumed >= flushThreshold ||
     *       consumed >= capacity * 0.25) {  // Large burst
     *       flushToRedis(newState, now);
     *   }
     */

    // ========================================================================
    // PART 8: MONITORING & OBSERVABILITY
    // ========================================================================

    /**
     * KEY METRICS TO TRACK
     * ════════════════════════════════════════════════════════════════════
     *
     * 1. Cache Hit Rate
     *    - local_cache_hits / total_requests
     *    - Target: >90%
     *
     * 2. Redis Call Rate
     *    - redis_calls / total_requests
     *    - Target: <10%
     *
     * 3. Flush Frequency
     *    - flushes_per_minute
     *    - Monitor for anomalies
     *
     * 4. State Drift
     *    - |local_tokens - redis_tokens|
     *    - Alert if >20% capacity
     *
     * 5. Pod Routing Consistency
     *    - requests_per_tenant_per_pod
     *    - Should be consistent with hashing
     *
     * 6. Failed Flushes
     *    - redis_write_errors
     *    - Alert on non-zero
     *
     * PROMETHEUS METRICS (Example):
     *
     * # HELP rate_limiter_local_cache_hits Local cache hits
     * # TYPE rate_limiter_local_cache_hits counter
     * rate_limiter_local_cache_hits{pod="pod-1",tenant="tenant1"} 450
     *
     * # HELP rate_limiter_redis_calls Redis calls made
     * # TYPE rate_limiter_redis_calls counter
     * rate_limiter_redis_calls{pod="pod-1",operation="read"} 10
     * rate_limiter_redis_calls{pod="pod-1",operation="write"} 45
     *
     * # HELP rate_limiter_flush_count Flushes to Redis
     * # TYPE rate_limiter_flush_count counter
     * rate_limiter_flush_count{pod="pod-1",tenant="tenant1"} 45
     */

    // ========================================================================
    // PART 9: DEMONSTRATION
    // ========================================================================

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=".repeat(80));
        System.out.println("BATCHED REDIS BUCKET4J ARCHITECTURE DEMO");
        System.out.println("=".repeat(80));

        // Setup
        MockRedisClient redis = new MockRedisClient();
        BucketManager manager = new BucketManager(redis);

        String tenant1 = "tenant_abc_123";

        System.out.println("\n[SCENARIO] Tenant makes 25 requests");
        System.out.println("Capacity: 100 tokens, Flush threshold: 10% (10 tokens)\n");

        // Simulate 25 requests
        for (int i = 1; i <= 25; i++) {
            boolean allowed = manager.allowRequest(tenant1, 1);
            System.out.printf("Request %2d: %s\n", i, allowed ? "✓ ALLOWED" : "✗ DENIED");

            if (i == 10) {
                System.out.println("    └─→ 10% consumed - FLUSH to Redis!");
            } else if (i == 20) {
                System.out.println("    └─→ 10% consumed again - FLUSH to Redis!");
            }

            Thread.sleep(50);  // Small delay
        }

        System.out.println("\n" + "─".repeat(80));
        System.out.println("RESULTS:");
        System.out.println("─".repeat(80));
        System.out.println("Total requests: 25");
        System.out.println("Redis calls: " + redis.getCallCount());
        System.out.println("Savings: " + (25 - redis.getCallCount()) + " Redis calls avoided!");
        System.out.println("Efficiency: " +
            String.format("%.1f%%", 100.0 * (1 - redis.getCallCount() / 25.0)) +
            " reduction in Redis load");

        manager.shutdown();
        System.out.println("\n[SHUTDOWN] Final flush completed");
        System.out.println("Total Redis calls after shutdown: " + redis.getCallCount());
    }
}

