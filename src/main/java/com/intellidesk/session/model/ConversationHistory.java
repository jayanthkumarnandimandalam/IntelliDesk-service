package com.intellidesk.session.model;

import java.time.Instant;
import java.util.List;

/**
 * Holds the full conversation history for a session.
 */
public record ConversationHistory(
        String sessionId,
        List<ConversationExchange> exchanges,
        Instant createdAt,
        Instant lastActivityAt,
        int maxExchanges
) {}
