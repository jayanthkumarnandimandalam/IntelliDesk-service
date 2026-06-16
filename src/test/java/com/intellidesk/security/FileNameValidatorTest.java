package com.intellidesk.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileNameValidatorTest {

    private FileNameValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FileNameValidator();
    }

    @Nested
    @DisplayName("Valid file names")
    class ValidFileNames {

        @Test
        @DisplayName("simple file name passes validation")
        void simpleFileName() {
            assertDoesNotThrow(() -> validator.validate("simple.md"));
        }

        @Test
        @DisplayName("file name with hyphens and version passes")
        void fileNameWithHyphensAndVersion() {
            assertDoesNotThrow(() -> validator.validate("document-v2.txt"));
        }

        @Test
        @DisplayName("file name with underscores passes")
        void fileNameWithUnderscores() {
            assertDoesNotThrow(() -> validator.validate("my_file.pdf"));
        }

        @Test
        @DisplayName("file name with spaces passes")
        void fileNameWithSpaces() {
            assertDoesNotThrow(() -> validator.validate("my document.txt"));
        }

        @Test
        @DisplayName("file name at exactly 255 characters passes")
        void maxLengthFileName() {
            String name = "a".repeat(251) + ".txt";
            assertEquals(255, name.length());
            assertDoesNotThrow(() -> validator.validate(name));
        }
    }

    @Nested
    @DisplayName("Path traversal rejection")
    class PathTraversal {

        @Test
        @DisplayName("rejects forward slash path traversal")
        void rejectsForwardSlashTraversal() {
            InvalidFileNameException ex = assertThrows(InvalidFileNameException.class,
                    () -> validator.validate("../secret.txt"));
            assertTrue(ex.getReason().contains("path traversal"));
        }

        @Test
        @DisplayName("rejects backslash path traversal")
        void rejectsBackslashTraversal() {
            InvalidFileNameException ex = assertThrows(InvalidFileNameException.class,
                    () -> validator.validate("..\\file.txt"));
            assertTrue(ex.getReason().contains("path traversal"));
        }

        @Test
        @DisplayName("rejects path traversal in the middle of path")
        void rejectsMiddleTraversal() {
            InvalidFileNameException ex = assertThrows(InvalidFileNameException.class,
                    () -> validator.validate("foo/../bar.txt"));
            assertTrue(ex.getReason().contains("path traversal"));
        }
    }

    @Nested
    @DisplayName("Absolute path rejection")
    class AbsolutePaths {

        @Test
        @DisplayName("rejects Unix absolute path")
        void rejectsUnixAbsolutePath() {
            InvalidFileNameException ex = assertThrows(InvalidFileNameException.class,
                    () -> validator.validate("/etc/passwd"));
            assertTrue(ex.getReason().contains("absolute path"));
        }

        @Test
        @DisplayName("rejects Windows absolute path with backslash")
        void rejectsWindowsAbsolutePathBackslash() {
            InvalidFileNameException ex = assertThrows(InvalidFileNameException.class,
                    () -> validator.validate("C:\\Windows\\file.txt"));
            assertTrue(ex.getReason().contains("absolute path"));
        }

        @Test
        @DisplayName("rejects Windows absolute path with forward slash")
        void rejectsWindowsAbsolutePathForwardSlash() {
            InvalidFileNameException ex = assertThrows(InvalidFileNameException.class,
                    () -> validator.validate("D:/documents/file.txt"));
            assertTrue(ex.getReason().contains("absolute path"));
        }

        @Test
        @DisplayName("rejects backslash absolute path")
        void rejectsBackslashAbsolutePath() {
            InvalidFileNameException ex = assertThrows(InvalidFileNameException.class,
                    () -> validator.validate("\\server\\share\\file.txt"));
            assertTrue(ex.getReason().contains("absolute path"));
        }
    }

    @Nested
    @DisplayName("Length validation")
    class LengthValidation {

        @Test
        @DisplayName("rejects file name exceeding 255 characters")
        void rejectsLongFileName() {
            String name = "a".repeat(256);
            InvalidFileNameException ex = assertThrows(InvalidFileNameException.class,
                    () -> validator.validate(name));
            assertTrue(ex.getReason().contains("exceeds maximum length"));
        }

        @Test
        @DisplayName("rejects much longer file name")
        void rejectsVeryLongFileName() {
            String name = "x".repeat(500) + ".txt";
            InvalidFileNameException ex = assertThrows(InvalidFileNameException.class,
                    () -> validator.validate(name));
            assertTrue(ex.getReason().contains("exceeds maximum length"));
        }
    }

    @Nested
    @DisplayName("Null byte rejection")
    class NullBytes {

        @Test
        @DisplayName("rejects file name containing null byte")
        void rejectsNullByte() {
            InvalidFileNameException ex = assertThrows(InvalidFileNameException.class,
                    () -> validator.validate("file\0name.txt"));
            assertTrue(ex.getReason().contains("null byte"));
        }

        @Test
        @DisplayName("rejects file name starting with null byte")
        void rejectsLeadingNullByte() {
            InvalidFileNameException ex = assertThrows(InvalidFileNameException.class,
                    () -> validator.validate("\0hidden.txt"));
            assertTrue(ex.getReason().contains("null byte"));
        }
    }

    @Nested
    @DisplayName("Control character rejection")
    class ControlCharacters {

        @Test
        @DisplayName("rejects file name with tab character")
        void rejectsTabCharacter() {
            InvalidFileNameException ex = assertThrows(InvalidFileNameException.class,
                    () -> validator.validate("file\tname.txt"));
            assertTrue(ex.getReason().contains("control character"));
        }

        @Test
        @DisplayName("rejects file name with newline character")
        void rejectsNewlineCharacter() {
            InvalidFileNameException ex = assertThrows(InvalidFileNameException.class,
                    () -> validator.validate("file\nname.txt"));
            assertTrue(ex.getReason().contains("control character"));
        }

        @Test
        @DisplayName("rejects file name with carriage return")
        void rejectsCarriageReturn() {
            InvalidFileNameException ex = assertThrows(InvalidFileNameException.class,
                    () -> validator.validate("file\rname.txt"));
            assertTrue(ex.getReason().contains("control character"));
        }

        @Test
        @DisplayName("rejects file name with bell character")
        void rejectsBellCharacter() {
            InvalidFileNameException ex = assertThrows(InvalidFileNameException.class,
                    () -> validator.validate("file\u0007name.txt"));
            assertTrue(ex.getReason().contains("control character"));
        }
    }

    @Nested
    @DisplayName("Null and blank input")
    class NullAndBlankInput {

        @Test
        @DisplayName("rejects null file name")
        void rejectsNull() {
            assertThrows(InvalidFileNameException.class,
                    () -> validator.validate(null));
        }

        @Test
        @DisplayName("rejects empty file name")
        void rejectsEmpty() {
            assertThrows(InvalidFileNameException.class,
                    () -> validator.validate(""));
        }

        @Test
        @DisplayName("rejects whitespace-only file name")
        void rejectsWhitespaceOnly() {
            assertThrows(InvalidFileNameException.class,
                    () -> validator.validate("   "));
        }
    }
}
