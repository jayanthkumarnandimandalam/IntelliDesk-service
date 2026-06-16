package com.intellidesk.security;

import com.intellidesk.config.AppConfig;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sliding window rate limiter that tracks per-session request timestamps.
 * Uses a 1-minute sliding window and rejects requests exceeding the configured limit.
 */
@Component
public class SlidingWindowRateLimiter {

    private static final long WINDOW_SIZE_MS = 60_000L; // 1 minute

    private final int maxRequestsPerMinute;
    private final ConcurrentHashMap<String, ConcurrentSkipListSet<Long>> requestTimestamps;
    private final Clock clock;
    private final ScheduledExecutorService cleanupExecutor;

    @org.springframework.beans.factory.annotation.Autowired
    public SlidingWindowRateLimiter(AppConfig appConfig) {
        this(appConfig, Clock.systemUTC());
    }

    /**
     * Constructor allowing clock injection for testing.
     */
    public SlidingWindowRateLimiter(AppConfig appConfig, Clock clock) {
        this.maxRequestsPerMinute = appConfig.rateLimitPerMinute();
        this.requestTimestamps = new ConcurrentHashMap<>();
        this.clock = clock;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limiter-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredEntries, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * Checks if a request from the given session is allowed within the rate limit.
     * If allowed, records the current timestamp.
     *
     * @param sessionId the session identifier
     * @return true if the request is within the limit, false if rate limit exceeded
     */
    public boolean isAllowed(String sessionId) {
        long now = clock.instant().toEpochMilli();
        long windowStart = now - WINDOW_SIZE_MS;

        ConcurrentSkipListSet<Long> timestamps = requestTimestamps.computeIfAbsent(
                sessionId, k -> new ConcurrentSkipListSet<>());

        // Remove timestamps outside the current window
        timestamps.headSet(windowStart).clear();

        // Check if within limit
        if (timestamps.size() >= maxRequestsPerMinute) {
            return false;
        }

        // Record this request - use a unique timestamp to avoid collisions
        long uniqueTimestamp = now;
        while (!timestamps.add(uniqueTimestamp)) {
            uniqueTimestamp++;
        }

        return true;
    }

    /**
     * Calculates the number of seconds until the next request will be allowed for the given session.
     *
     * @param sessionId the session identifier
     * @return seconds until next allowed request, or 0 if already allowed
     */
    public long getRetryAfterSeconds(String sessionId) {
        long now = clock.instant().toEpochMilli();
        long windowStart = now - WINDOW_SIZE_MS;

        ConcurrentSkipListSet<Long> timestamps = requestTimestamps.get(sessionId);
        if (timestamps == null || timestamps.size() < maxRequestsPerMinute) {
            return 0;
        }

        // Remove expired entries
        timestamps.headSet(windowStart).clear();

        if (timestamps.size() < maxRequestsPerMinute) {
            return 0;
        }

        // The oldest timestamp in the window determines when the next slot opens
        Long oldest = timestamps.first();
        long retryAfterMs = (oldest + WINDOW_SIZE_MS) - now;
        return Math.max(1, (long) Math.ceil(retryAfterMs / 1000.0));
    }

    /**
     * Periodically removes sessions with no recent activity to prevent memory leaks.
     */
    void cleanupExpiredEntries() {
        long windowStart = clock.instant().toEpochMilli() - WINDOW_SIZE_MS;

        requestTimestamps.entrySet().removeIf(entry -> {
            entry.getValue().headSet(windowStart).clear();
            return entry.getValue().isEmpty();
        });
    }

    /**
     * Returns the configured max requests per minute (for testing/monitoring).
     */
    public int getMaxRequestsPerMinute() {
        return maxRequestsPerMinute;
    }
}
