package com.intellidesk.security;

import org.springframework.stereotype.Component;

/**
 * Validates file names submitted during document ingestion.
 * Rejects names containing path traversal sequences, absolute paths,
 * excessive length, null bytes, or control characters.
 */
@Component
public class FileNameValidator {

    private static final int MAX_FILE_NAME_LENGTH = 255;

    /**
     * Validates the given file name.
     *
     * @param fileName the file name to validate
     * @throws InvalidFileNameException if the file name is invalid
     */
    public void validate(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new InvalidFileNameException(fileName == null ? "" : fileName,
                    "File name must not be null or blank");
        }

        checkPathTraversal(fileName);
        checkAbsolutePath(fileName);
        checkLength(fileName);
        checkNullBytes(fileName);
        checkControlCharacters(fileName);
    }

    private void checkPathTraversal(String fileName) {
        if (fileName.contains("../") || fileName.contains("..\\")) {
            throw new InvalidFileNameException(fileName,
                    "File name contains path traversal sequence");
        }
    }

    private void checkAbsolutePath(String fileName) {
        if (fileName.startsWith("/") || fileName.startsWith("\\")) {
            throw new InvalidFileNameException(fileName,
                    "File name must not be an absolute path");
        }
        // Check for Windows drive letter pattern (e.g., C:\, D:/)
        if (fileName.length() >= 3
                && Character.isLetter(fileName.charAt(0))
                && fileName.charAt(1) == ':'
                && (fileName.charAt(2) == '\\' || fileName.charAt(2) == '/')) {
            throw new InvalidFileNameException(fileName,
                    "File name must not be an absolute path");
        }
    }

    private void checkLength(String fileName) {
        if (fileName.length() > MAX_FILE_NAME_LENGTH) {
            throw new InvalidFileNameException(fileName.substring(0, 50) + "...",
                    "File name exceeds maximum length of " + MAX_FILE_NAME_LENGTH + " characters");
        }
    }

    private void checkNullBytes(String fileName) {
        if (fileName.indexOf('\0') >= 0) {
            throw new InvalidFileNameException(fileName.replace("\0", ""),
                    "File name contains null byte");
        }
    }

    private void checkControlCharacters(String fileName) {
        for (int i = 0; i < fileName.length(); i++) {
            char c = fileName.charAt(i);
            if (c < 32 && c != '\0') { // null bytes handled separately
                throw new InvalidFileNameException(fileName,
                        "File name contains control character at position " + i);
            }
        }
    }
}
