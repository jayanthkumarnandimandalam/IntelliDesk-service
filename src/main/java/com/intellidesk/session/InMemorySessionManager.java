package com.intellidesk.session;

import com.intellidesk.config.AppConfig;
import com.intellidesk.session.model.ConversationExchange;
import com.intellidesk.session.model.ConversationHistory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link SessionManager} using ConcurrentHashMap.
 * Thread-safe for concurrent access across multiple sessions.
 * Uses lazy expiration — sessions are checked for TTL on access.
 */
@Component
public class InMemorySessionManager implements SessionManager {

    private final ConcurrentHashMap<String, MutableSessionData> sessions = new ConcurrentHashMap<>();
    private final int contextWindowSize;
    private final Duration sessionTimeout;

    public InMemorySessionManager(AppConfig appConfig) {
        this.contextWindowSize = clampContextWindow(appConfig.contextWindowSize());
        this.sessionTimeout = Duration.ofMinutes(clampTimeout(appConfig.sessionTimeoutMinutes()));
    }

    @Override
    public ConversationHistory getHistory(String sessionId) {
        cleanupExpiredSessions();

        MutableSessionData data = sessions.get(sessionId);
        if (data == null || isExpired(data)) {
            if (data != null) {
                sessions.remove(sessionId);
            }
            return createEmptyHistory(sessionId);
        }

        return data.toConversationHistory();
    }

    @Override
    public void addExchange(String sessionId, ConversationExchange exchange) {
        cleanupExpiredSessions();

        sessions.compute(sessionId, (key, existing) -> {
            if (existing == null || isExpired(existing)) {
                // Create new session data
                MutableSessionData newData = new MutableSessionData(
                        sessionId, contextWindowSize);
                newData.addExchange(exchange);
                return newData;
            }

            existing.addExchange(exchange);
            return existing;
        });
    }

    @Override
    public void expireSession(String sessionId) {
        sessions.remove(sessionId);
    }

    @Override
    public boolean isSessionActive(String sessionId) {
        MutableSessionData data = sessions.get(sessionId);
        if (data == null) {
            return false;
        }
        if (isExpired(data)) {
            sessions.remove(sessionId);
            return false;
        }
        return true;
    }

    private boolean isExpired(MutableSessionData data) {
        return Duration.between(data.getLastActivityAt(), Instant.now()).compareTo(sessionTimeout) > 0;
    }

    private ConversationHistory createEmptyHistory(String sessionId) {
        Instant now = Instant.now();
        return new ConversationHistory(
                sessionId,
                Collections.emptyList(),
                now,
                now,
                contextWindowSize
        );
    }

    private void cleanupExpiredSessions() {
        sessions.entrySet().removeIf(entry -> isExpired(entry.getValue()));
    }

    private static int clampContextWindow(int value) {
        return Math.max(1, Math.min(50, value));
    }

    private static int clampTimeout(int value) {
        return Math.max(1, Math.min(1440, value));
    }

    /**
     * Mutable internal representation of a session's data.
     * Synchronization is handled by ConcurrentHashMap's compute methods.
     */
    private static class MutableSessionData {
        private final String sessionId;
        private final int maxExchanges;
        private final List<ConversationExchange> exchanges;
        private final Instant createdAt;
        private volatile Instant lastActivityAt;

        MutableSessionData(String sessionId, int maxExchanges) {
            this.sessionId = sessionId;
            this.maxExchanges = maxExchanges;
            this.exchanges = new ArrayList<>();
            this.createdAt = Instant.now();
            this.lastActivityAt = this.createdAt;
        }

        void addExchange(ConversationExchange exchange) {
            exchanges.add(exchange);
            // Trim oldest if window exceeded, preserving chronological order
            while (exchanges.size() > maxExchanges) {
                exchanges.remove(0);
            }
            lastActivityAt = Instant.now();
        }

        Instant getLastActivityAt() {
            return lastActivityAt;
        }

        ConversationHistory toConversationHistory() {
            return new ConversationHistory(
                    sessionId,
                    Collections.unmodifiableList(new ArrayList<>(exchanges)),
                    createdAt,
                    lastActivityAt,
                    maxExchanges
            );
        }
    }
}
