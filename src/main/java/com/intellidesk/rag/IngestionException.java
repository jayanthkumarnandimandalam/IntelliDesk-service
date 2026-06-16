package com.intellidesk.rag;

/**
 * Exception thrown during document ingestion operations.
 * Carries an HTTP status code to facilitate proper error responses.
 */
public class IngestionException extends RuntimeException {

    private final int statusCode;

    public IngestionException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public IngestionException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /**
     * Returns the HTTP status code associated with this ingestion error.
     */
    public int getStatusCode() {
        return statusCode;
    }
}
