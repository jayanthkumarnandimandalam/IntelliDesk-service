package com.intellidesk.evaluation;

/**
 * Exception thrown during evaluation operations.
 * Carries an HTTP status code to facilitate proper error responses.
 */
public class EvaluationException extends RuntimeException {

    private final int statusCode;

    public EvaluationException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public EvaluationException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /**
     * Returns the HTTP status code associated with this evaluation error.
     */
    public int getStatusCode() {
        return statusCode;
    }
}
