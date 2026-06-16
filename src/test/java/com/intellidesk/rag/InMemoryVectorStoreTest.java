package com.intellidesk.rag;

import com.intellidesk.rag.model.ChunkMetadata;
import com.intellidesk.rag.model.RetrievedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InMemoryVectorStore}.
 */
class InMemoryVectorStoreTest {

    private InMemoryVectorStore store;
    private Instant now;

    @BeforeEach
    void setUp() {
        store = new InMemoryVectorStore();
        now = Instant.now();
    }

    @Test
    void store_andRetrieve() {
        List<String> chunks = List.of("password reset instructions", "email configuration guide");
        List<ChunkMetadata> metadata = List.of(
                new ChunkMetadata("doc.md", "docs/doc.md", ".md", now, "Password Reset"),
                new ChunkMetadata("doc.md", "docs/doc.md", ".md", now, "Email Config")
        );

        store.store(chunks, metadata);

        assertEquals(2, store.size());
        List<RetrievedChunk> results = store.retrieve("password reset", 5, 0.5);
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).content().contains("password"));
    }

    @Test
    void delete_removesChunksForFilePath() {
        List<String> chunks = List.of("content one", "content two");
        List<ChunkMetadata> metadata = List.of(
                new ChunkMetadata("file.txt", "path/file.txt", ".txt", now, "Title"),
                new ChunkMetadata("file.txt", "path/file.txt", ".txt", now, "Title")
        );

        store.store(chunks, metadata);
        assertEquals(2, store.size());

        store.delete("path/file.txt");
        assertEquals(0, store.size());
    }

    @Test
    void retrieve_emptyQuery_returnsEmpty() {
        List<String> chunks = List.of("some content");
        List<ChunkMetadata> metadata = List.of(
                new ChunkMetadata("f.txt", "f.txt", ".txt", now, "Title")
        );
        store.store(chunks, metadata);

        assertTrue(store.retrieve("", 5, 0.5).isEmpty());
        assertTrue(store.retrieve(null, 5, 0.5).isEmpty());
    }

    @Test
    void retrieve_respectsTopK() {
        for (int i = 0; i < 10; i++) {
            String path = "file" + i + ".txt";
            store.store(
                    List.of("common word content " + i),
                    List.of(new ChunkMetadata(path, path, ".txt", now, "Title"))
            );
        }

        List<RetrievedChunk> results = store.retrieve("common word content", 3, 0.0);
        assertTrue(results.size() <= 3);
    }

    @Test
    void retrieve_respectsSimilarityThreshold() {
        store.store(
                List.of("java programming guide"),
                List.of(new ChunkMetadata("java.txt", "java.txt", ".txt", now, "Java"))
        );
        store.store(
                List.of("cooking recipes for dinner"),
                List.of(new ChunkMetadata("cook.txt", "cook.txt", ".txt", now, "Cooking"))
        );

        // High threshold - only very relevant results
        List<RetrievedChunk> results = store.retrieve("java programming", 10, 0.9);
        for (RetrievedChunk chunk : results) {
            assertTrue(chunk.similarityScore() >= 0.9);
        }
    }

    @Test
    void store_replacesExistingChunksForSameFilePath() {
        String filePath = "docs/readme.md";
        store.store(
                List.of("original content"),
                List.of(new ChunkMetadata("readme.md", filePath, ".md", now, "Original"))
        );
        assertEquals(1, store.sizeForFile(filePath));

        // Store new chunks for same path - should replace
        store.store(
                List.of("new content one", "new content two"),
                List.of(
                        new ChunkMetadata("readme.md", filePath, ".md", now, "New1"),
                        new ChunkMetadata("readme.md", filePath, ".md", now, "New2")
                )
        );
        assertEquals(2, store.sizeForFile(filePath));
        assertEquals(2, store.size());
    }

    @Test
    void store_nullArguments_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> store.store(null, null));
        assertThrows(IllegalArgumentException.class,
                () -> store.store(List.of("text"), List.of()));
    }

    @Test
    void containsFile_returnsTrueForStoredFile() {
        store.store(
                List.of("content"),
                List.of(new ChunkMetadata("f.txt", "path/f.txt", ".txt", now, "Title"))
        );

        assertTrue(store.containsFile("path/f.txt"));
        assertFalse(store.containsFile("other/path.txt"));
    }
}
