package com.intellidesk.security;

/**
 * Thrown when a file name fails validation during ingestion.
 * Contains the specific reason for rejection.
 */
public class InvalidFileNameException extends RuntimeException {

    private final String fileName;
    private final String reason;

    public InvalidFileNameException(String fileName, String reason) {
        super("Invalid file name '" + fileName + "': " + reason);
        this.fileName = fileName;
        this.reason = reason;
    }

    public String getFileName() {
        return fileName;
    }

    public String getReason() {
        return reason;
    }
}
