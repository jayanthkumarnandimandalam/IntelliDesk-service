package com.intellidesk.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TextChunker}.
 */
class TextChunkerTest {

    private TextChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new TextChunker();
    }

    @Test
    void splitIntoChunks_basicChunking() {
        // 10 words, chunk size 4, overlap 1 => step = 3
        // Chunks: [0..3], [3..6], [6..9], [9..9]
        String text = "one two three four five six seven eight nine ten";
        List<String> chunks = chunker.splitIntoChunks(text, 4, 1);

        assertEquals(4, chunks.size());
        assertEquals("one two three four", chunks.get(0));
        assertEquals("four five six seven", chunks.get(1));
        assertEquals("seven eight nine ten", chunks.get(2));
        assertEquals("ten", chunks.get(3));
    }

    @Test
    void splitIntoChunks_overlapBehavior() {
        // 8 words, chunk size 5, overlap 2 => step = 3
        // Chunks: [0..4], [3..7]
        String text = "alpha beta gamma delta epsilon zeta eta theta";
        List<String> chunks = chunker.splitIntoChunks(text, 5, 2);

        assertEquals(3, chunks.size());
        assertEquals("alpha beta gamma delta epsilon", chunks.get(0));
        assertEquals("delta epsilon zeta eta theta", chunks.get(1));
        assertEquals("eta theta", chunks.get(2));

        // Verify overlap: last 2 words of first chunk appear as first 2 words of second chunk
        String[] firstChunkWords = chunks.get(0).split("\\s+");
        String[] secondChunkWords = chunks.get(1).split("\\s+");
        assertEquals(firstChunkWords[3], secondChunkWords[0]); // "delta"
        assertEquals(firstChunkWords[4], secondChunkWords[1]); // "epsilon"
    }

    @Test
    void splitIntoChunks_textShorterThanChunkSize() {
        String text = "hello world";
        List<String> chunks = chunker.splitIntoChunks(text, 10, 2);

        assertEquals(1, chunks.size());
        assertEquals("hello world", chunks.get(0));
    }

    @Test
    void splitIntoChunks_textExactlyChunkSize() {
        String text = "one two three four five";
        List<String> chunks = chunker.splitIntoChunks(text, 5, 1);

        assertEquals(1, chunks.size());
        assertEquals("one two three four five", chunks.get(0));
    }

    @Test
    void splitIntoChunks_emptyText() {
        List<String> chunks = chunker.splitIntoChunks("", 10, 2);
        assertTrue(chunks.isEmpty());
    }

    @Test
    void splitIntoChunks_nullText() {
        List<String> chunks = chunker.splitIntoChunks(null, 10, 2);
        assertTrue(chunks.isEmpty());
    }

    @Test
    void splitIntoChunks_whitespaceOnlyText() {
        List<String> chunks = chunker.splitIntoChunks("   \t  \n  ", 10, 2);
        assertTrue(chunks.isEmpty());
    }

    @Test
    void splitIntoChunks_zeroOverlap() {
        // 6 words, chunk size 3, overlap 0 => step = 3
        String text = "one two three four five six";
        List<String> chunks = chunker.splitIntoChunks(text, 3, 0);

        assertEquals(2, chunks.size());
        assertEquals("one two three", chunks.get(0));
        assertEquals("four five six", chunks.get(1));
    }

    @Test
    void splitIntoChunks_singleWordChunks() {
        String text = "one two three";
        List<String> chunks = chunker.splitIntoChunks(text, 1, 0);

        assertEquals(3, chunks.size());
        assertEquals("one", chunks.get(0));
        assertEquals("two", chunks.get(1));
        assertEquals("three", chunks.get(2));
    }

    @Test
    void splitIntoChunks_invalidChunkSize_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> chunker.splitIntoChunks("text", 0, 0));
    }

    @Test
    void splitIntoChunks_negativeOverlap_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> chunker.splitIntoChunks("text", 5, -1));
    }

    @Test
    void splitIntoChunks_overlapEqualToChunkSize_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> chunker.splitIntoChunks("text", 5, 5));
    }

    @Test
    void splitIntoChunks_overlapGreaterThanChunkSize_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> chunker.splitIntoChunks("text", 5, 6));
    }
}
