package com.intellidesk.session.model;

import java.time.Instant;

/**
 * Represents a single exchange (user message + assistant response) in a conversation.
 */
public record ConversationExchange(
        String userMessage,
        String assistantResponse,
        Instant timestamp
) {}
