package com.intellidesk.security;

import com.intellidesk.config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SlidingWindowRateLimiter.
 * Tests core sliding window behavior: allowing requests within limits,
 * blocking when exceeded, window expiration, and Retry-After calculation.
 */
class SlidingWindowRateLimiterTest {

    private static final int MAX_REQUESTS = 5; // Low limit for testing
    private Clock clock;
    private SlidingWindowRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        AppConfig appConfig = mock(AppConfig.class);
        when(appConfig.rateLimitPerMinute()).thenReturn(MAX_REQUESTS);
        clock = Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneId.of("UTC"));
        rateLimiter = new SlidingWindowRateLimiter(appConfig, clock);
    }

    @Test
    @DisplayName("Requests within limit should be allowed")
    void requestsWithinLimitAllowed() {
        String sessionId = "session-1";

        for (int i = 0; i < MAX_REQUESTS; i++) {
            assertTrue(rateLimiter.isAllowed(sessionId),
                    "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    @DisplayName("Requests exceeding limit should be blocked")
    void requestsExceedingLimitBlocked() {
        String sessionId = "session-2";

        // Use up all allowed requests
        for (int i = 0; i < MAX_REQUESTS; i++) {
            assertTrue(rateLimiter.isAllowed(sessionId));
        }

        // Next request should be blocked
        assertFalse(rateLimiter.isAllowed(sessionId),
                "Request exceeding limit should be blocked");
    }

    @Test
    @DisplayName("Different sessions should have independent limits")
    void differentSessionsIndependentLimits() {
        String session1 = "session-a";
        String session2 = "session-b";

        // Fill up session1
        for (int i = 0; i < MAX_REQUESTS; i++) {
            assertTrue(rateLimiter.isAllowed(session1));
        }
        assertFalse(rateLimiter.isAllowed(session1));

        // session2 should still be allowed
        assertTrue(rateLimiter.isAllowed(session2),
                "Different session should not be affected");
    }

    @Test
    @DisplayName("Sliding window allows new requests after old ones expire")
    void slidingWindowAllowsAfterExpiry() {
        // Use a mutable clock for this test
        MutableClock mutableClock = new MutableClock(Instant.parse("2024-01-15T10:00:00Z"));
        AppConfig appConfig = mock(AppConfig.class);
        when(appConfig.rateLimitPerMinute()).thenReturn(MAX_REQUESTS);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(appConfig, mutableClock);

        String sessionId = "session-sliding";

        // Fill up the limit
        for (int i = 0; i < MAX_REQUESTS; i++) {
            assertTrue(limiter.isAllowed(sessionId));
        }
        assertFalse(limiter.isAllowed(sessionId), "Should be blocked at limit");

        // Advance past the window (61 seconds)
        mutableClock.advance(61_000);

        // Now requests should be allowed again
        assertTrue(limiter.isAllowed(sessionId),
                "Should be allowed after window expires");
    }

    @Test
    @DisplayName("Retry-After returns 0 when within limit")
    void retryAfterZeroWhenWithinLimit() {
        String sessionId = "session-retry-ok";
        rateLimiter.isAllowed(sessionId); // 1 request

        assertEquals(0, rateLimiter.getRetryAfterSeconds(sessionId));
    }

    @Test
    @DisplayName("Retry-After returns positive seconds when limit exceeded")
    void retryAfterPositiveWhenExceeded() {
        MutableClock mutableClock = new MutableClock(Instant.parse("2024-01-15T10:00:00Z"));
        AppConfig appConfig = mock(AppConfig.class);
        when(appConfig.rateLimitPerMinute()).thenReturn(MAX_REQUESTS);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(appConfig, mutableClock);

        String sessionId = "session-retry";

        // Fill up the limit
        for (int i = 0; i < MAX_REQUESTS; i++) {
            limiter.isAllowed(sessionId);
        }

        // Advance clock by 10 seconds
        mutableClock.advance(10_000);

        long retryAfter = limiter.getRetryAfterSeconds(sessionId);
        // Oldest timestamp was at t=0, so window opens at t+60s. Current time is t+10s.
        // Retry after should be approximately 50 seconds
        assertTrue(retryAfter > 0, "Retry-After should be positive");
        assertTrue(retryAfter <= 60, "Retry-After should be at most 60 seconds");
        assertEquals(50, retryAfter, "Retry-After should be ~50 seconds");
    }

    @Test
    @DisplayName("Retry-After returns 0 for unknown session")
    void retryAfterZeroForUnknownSession() {
        assertEquals(0, rateLimiter.getRetryAfterSeconds("unknown-session"));
    }

    @Test
    @DisplayName("Cleanup removes expired sessions")
    void cleanupRemovesExpiredSessions() {
        MutableClock mutableClock = new MutableClock(Instant.parse("2024-01-15T10:00:00Z"));
        AppConfig appConfig = mock(AppConfig.class);
        when(appConfig.rateLimitPerMinute()).thenReturn(MAX_REQUESTS);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(appConfig, mutableClock);

        String sessionId = "session-cleanup";
        limiter.isAllowed(sessionId);

        // Advance past the window
        mutableClock.advance(61_000);

        // Trigger cleanup
        limiter.cleanupExpiredEntries();

        // After cleanup, session should have full capacity
        for (int i = 0; i < MAX_REQUESTS; i++) {
            assertTrue(limiter.isAllowed(sessionId));
        }
    }

    /**
     * A mutable clock implementation for testing time-dependent behavior.
     */
    private static class MutableClock extends Clock {
        private Instant currentInstant;

        MutableClock(Instant startInstant) {
            this.currentInstant = startInstant;
        }

        void advance(long millis) {
            this.currentInstant = this.currentInstant.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }
}
