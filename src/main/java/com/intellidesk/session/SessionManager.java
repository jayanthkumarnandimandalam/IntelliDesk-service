package com.intellidesk.session;

import com.intellidesk.session.model.ConversationExchange;
import com.intellidesk.session.model.ConversationHistory;

/**
 * Manages conversation sessions and their history.
 * Implementations must be thread-safe.
 */
public interface SessionManager {

    /**
     * Retrieves the conversation history for the given session.
     * Returns an empty history if the session does not exist.
     *
     * @param sessionId the session identifier
     * @return the conversation history for the session
     */
    ConversationHistory getHistory(String sessionId);

    /**
     * Adds a new exchange to the session's conversation history.
     * If the session does not exist, it is created.
     * If the context window is exceeded, the oldest exchanges are discarded.
     *
     * @param sessionId the session identifier
     * @param exchange the exchange to add
     */
    void addExchange(String sessionId, ConversationExchange exchange);

    /**
     * Expires and removes the session data.
     *
     * @param sessionId the session identifier
     */
    void expireSession(String sessionId);

    /**
     * Checks whether a session is currently active (not expired).
     *
     * @param sessionId the session identifier
     * @return true if the session is active, false otherwise
     */
    boolean isSessionActive(String sessionId);
}
