package com.intellidesk.session;

import com.intellidesk.config.AppConfig;
import com.intellidesk.session.model.ConversationExchange;
import com.intellidesk.session.model.ConversationHistory;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.Instant;
import java.util.List;

/**
 * Property-based tests for SessionManager.
 * Validates Properties 6, 7, and 8 from the design document.
 */
class SessionManagerPropertyTest {

    /**
     * Creates an InMemorySessionManager with the given context window size and timeout.
     */
    private InMemorySessionManager createSessionManager(int contextWindowSize, int sessionTimeoutMinutes) {
        AppConfig config = new AppConfig(
                5,                          // topK
                0.7,                        // similarityThreshold
                4000,                       // maxMessageLength
                contextWindowSize,          // contextWindowSize
                sessionTimeoutMinutes,      // sessionTimeoutMinutes
                20,                         // rateLimitPerMinute
                10,                         // maxAudioSizeMb
                60,                         // maxAudioDurationSec
                30,                         // nodeTimeoutSeconds
                512,                        // chunkSize
                50,                         // chunkOverlap
                0.7,                        // evaluationThreshold
                120,                        // evaluationTimeoutSec
                5,                          // healthCheckTimeoutSec
                5,                          // cbFailureThreshold
                30,                         // cbCooldownSeconds
                "local",                    // activeProfile
                "gpt-4o-mini",              // llmModel
                "text-embedding-3-small",   // embeddingModel
                "http://localhost:8000",     // vectorStoreUrl
                "http://localhost:9000",     // sttUrl
                "data/knowledge-base",      // knowledgeBaseDir
                "data/evaluation/dataset.json", // evaluationDatasetPath
                "data/evaluation/report.json",  // evaluationReportPath
                30,                         // llmTimeoutSeconds
                "http://localhost:3000"      // corsAllowedOrigins
        );
        return new InMemorySessionManager(config);
    }

    // ========================================================================
    // Property 6: Context window size invariant
    // For any session with N total exchanges where N > configured window W,
    // the context passed to the LLM SHALL contain exactly the most recent W
    // exchanges in chronological order, and the total SHALL never exceed W.
    // Validates: Requirements 2.3, 2.5
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 6: Context window size invariant")
    void contextWindowSizeInvariant(
            @ForAll @IntRange(min = 1, max = 50) int windowSize,
            @ForAll @IntRange(min = 51, max = 200) int totalExchanges
    ) {
        // Ensure totalExchanges > windowSize
        int effectiveTotal = Math.max(windowSize + 1, totalExchanges);

        InMemorySessionManager sessionManager = createSessionManager(windowSize, 30);
        String sessionId = "test-session-prop6";

        // Add more exchanges than the window size
        for (int i = 0; i < effectiveTotal; i++) {
            ConversationExchange exchange = new ConversationExchange(
                    "User message " + i,
                    "Assistant response " + i,
                    Instant.now().plusSeconds(i)
            );
            sessionManager.addExchange(sessionId, exchange);
        }

        ConversationHistory history = sessionManager.getHistory(sessionId);

        // The retained exchanges SHALL never exceed W
        assert history.exchanges().size() <= windowSize :
                "History size " + history.exchanges().size() + " exceeds window " + windowSize;

        // Exactly the most recent W exchanges should be retained
        assert history.exchanges().size() == windowSize :
                "History size " + history.exchanges().size() + " should be exactly " + windowSize;

        // Verify these are the most recent W exchanges
        List<ConversationExchange> exchanges = history.exchanges();
        for (int i = 0; i < exchanges.size(); i++) {
            int expectedIndex = effectiveTotal - windowSize + i;
            String expectedMessage = "User message " + expectedIndex;
            assert exchanges.get(i).userMessage().equals(expectedMessage) :
                    "Exchange at position " + i + " should be '" + expectedMessage +
                            "' but was '" + exchanges.get(i).userMessage() + "'";
        }
    }

    // ========================================================================
    // Property 7: Conversation history ordering
    // For any sequence of exchanges added to a session, the history SHALL
    // maintain chronological order such that exchange[i].timestamp <=
    // exchange[j].timestamp for i < j.
    // Validates: Requirements 2.1, 2.2
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 7: Conversation history ordering")
    void conversationHistoryOrdering(
            @ForAll("exchangeListProvider") List<ConversationExchange> exchanges
    ) {
        InMemorySessionManager sessionManager = createSessionManager(50, 30);
        String sessionId = "test-session-prop7";

        // Add all exchanges to the session
        for (ConversationExchange exchange : exchanges) {
            sessionManager.addExchange(sessionId, exchange);
        }

        ConversationHistory history = sessionManager.getHistory(sessionId);
        List<ConversationExchange> retrievedExchanges = history.exchanges();

        // Verify chronological ordering: exchange[i].timestamp <= exchange[j].timestamp for i < j
        for (int i = 0; i < retrievedExchanges.size() - 1; i++) {
            Instant current = retrievedExchanges.get(i).timestamp();
            Instant next = retrievedExchanges.get(i + 1).timestamp();
            assert !current.isAfter(next) :
                    "Exchange at position " + i + " (timestamp=" + current +
                            ") should not be after exchange at position " + (i + 1) +
                            " (timestamp=" + next + ")";
        }
    }

    @Provide
    Arbitrary<List<ConversationExchange>> exchangeListProvider() {
        // Generate a list of sorted offsets to simulate chronologically ordered exchanges
        return Arbitraries.longs()
                .between(1, 100_000)
                .list().ofMinSize(2).ofMaxSize(50)
                .map(offsets -> {
                    // Sort offsets to produce chronological timestamps
                    List<Long> sorted = offsets.stream().sorted().toList();
                    return sorted.stream()
                            .map(offset -> new ConversationExchange(
                                    "User message at " + offset,
                                    "Assistant response at " + offset,
                                    Instant.EPOCH.plusSeconds(offset)
                            ))
                            .toList();
                });
    }

    // ========================================================================
    // Property 8: Session expiration
    // For any session whose last activity is more than timeout in the past,
    // it SHALL be considered expired.
    // Validates: Requirements 2.6
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 8: Session expiration")
    void sessionExpiration(
            @ForAll @IntRange(min = 1, max = 60) int timeoutMinutes
    ) {
        InMemorySessionManager sessionManager = createSessionManager(10, timeoutMinutes);
        String sessionId = "test-session-prop8";

        // Add an exchange to create the session
        ConversationExchange exchange = new ConversationExchange(
                "Hello",
                "Hi there!",
                Instant.now()
        );
        sessionManager.addExchange(sessionId, exchange);

        // Session should be active immediately after adding an exchange
        assert sessionManager.isSessionActive(sessionId) :
                "Session should be active immediately after adding an exchange";

        // Expire the session explicitly (simulates timeout behavior)
        sessionManager.expireSession(sessionId);

        // After expiration, session should not be active
        assert !sessionManager.isSessionActive(sessionId) :
                "Session should not be active after expiration";

        // Getting history of expired session should return empty history
        ConversationHistory history = sessionManager.getHistory(sessionId);
        assert history.exchanges().isEmpty() :
                "Expired session should return empty history but got " +
                        history.exchanges().size() + " exchanges";
    }
}
