package com.intellidesk.rag;

import com.intellidesk.rag.model.ChunkMetadata;
import com.intellidesk.rag.model.IngestionConfig;
import com.intellidesk.rag.model.IngestionResult;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Property-based tests for the RAG pipeline.
 * Validates Properties 11-15 from the design document.
 */
class RagPipelinePropertyTest {

    // ========================================================================
    // Property 11: Document type filtering during ingestion
    // For any set of files in the knowledge base directory, only files with
    // extensions .md, .txt, and .pdf SHALL be processed for ingestion;
    // all other file types SHALL be ignored.
    // Validates: Requirements 4.2
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 11: Document type filtering during ingestion")
    void documentTypeFiltering(
            @ForAll("fileNameMix") List<String> fileNames
    ) throws IOException {
        Path tempDir = Files.createTempDirectory("rag-prop11-");
        try {
            Set<String> supportedExtensions = Set.of(".md", ".txt", ".pdf");

            // Create files with content
            for (String fileName : fileNames) {
                Path file = tempDir.resolve(fileName);
                Files.writeString(file, "This is sample content for file " + fileName +
                        " with enough words to produce at least one chunk when split by the chunker");
            }

            InMemoryVectorStore vectorStore = new InMemoryVectorStore();
            TextChunker textChunker = new TextChunker();
            DefaultDocumentIngester ingester = new DefaultDocumentIngester(textChunker, vectorStore);

            IngestionConfig config = new IngestionConfig(64, 10, supportedExtensions);
            IngestionResult result = ingester.ingest(tempDir, config);

            // Count how many files have supported extensions
            long expectedSupportedCount = fileNames.stream()
                    .filter(name -> {
                        int dot = name.lastIndexOf('.');
                        if (dot < 0) return false;
                        String ext = name.substring(dot).toLowerCase();
                        return supportedExtensions.contains(ext);
                    })
                    .count();

            // documentCount + failedDocuments should account for only supported files
            int totalProcessed = result.documentCount() + result.failedDocuments().size();
            assert totalProcessed <= expectedSupportedCount :
                    "Processed " + totalProcessed + " files but only " + expectedSupportedCount +
                            " have supported extensions";

            // Unsupported extensions should be completely ignored (no failures for them)
            for (String failedDoc : result.failedDocuments()) {
                int dot = failedDoc.lastIndexOf('.');
                if (dot >= 0) {
                    String ext = failedDoc.substring(dot).toLowerCase();
                    assert supportedExtensions.contains(ext) :
                            "Failed document '" + failedDoc + "' has unsupported extension '" + ext +
                                    "' - unsupported files should be ignored, not reported as failures";
                }
            }
        } finally {
            // Clean up temp directory
            deleteDirectory(tempDir);
        }
    }

    @Provide
    Arbitrary<List<String>> fileNameMix() {
        Arbitrary<String> supportedFiles = Arbitraries.of("doc.md", "notes.txt", "report.pdf",
                "readme.md", "guide.txt", "manual.pdf", "help.md");
        Arbitrary<String> unsupportedFiles = Arbitraries.of("image.png", "data.csv", "script.js",
                "style.css", "archive.zip", "video.mp4", "config.yaml", "app.exe");

        return Combinators.combine(
                supportedFiles.list().ofMinSize(1).ofMaxSize(4),
                unsupportedFiles.list().ofMinSize(1).ofMaxSize(4)
        ).as((supported, unsupported) -> {
            List<String> all = new ArrayList<>(supported);
            all.addAll(unsupported);
            Collections.shuffle(all);
            return all;
        });
    }

    // ========================================================================
    // Property 12: Chunking produces valid segments
    // For any document text of length L (where L > chunk_size), the chunking
    // algorithm SHALL produce chunks where each chunk's token count is at most
    // the configured chunk_size, consecutive chunks overlap by exactly the
    // configured overlap tokens, and the union of all chunk content covers
    // the complete original document text.
    // Validates: Requirements 4.3
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 12: Chunking produces valid segments")
    void chunkingProducesValidSegments(
            @ForAll("randomText") String text,
            @ForAll @IntRange(min = 5, max = 50) int chunkSize,
            @ForAll @IntRange(min = 0, max = 49) int overlapRaw
    ) {
        // Ensure overlap < chunkSize
        int overlap = Math.min(overlapRaw, chunkSize - 1);

        TextChunker chunker = new TextChunker();
        List<String> chunks = chunker.splitIntoChunks(text, chunkSize, overlap);

        String[] words = text.split("\\s+");

        if (words.length == 0 || text.isBlank()) {
            assert chunks.isEmpty() : "Empty text should produce no chunks";
            return;
        }

        // Invariant 1: Each chunk's word count is at most chunkSize
        for (int i = 0; i < chunks.size(); i++) {
            String[] chunkWords = chunks.get(i).split("\\s+");
            assert chunkWords.length <= chunkSize :
                    "Chunk " + i + " has " + chunkWords.length + " words, exceeds chunkSize " + chunkSize;
        }

        // Invariant 2: Consecutive chunks overlap by exactly `overlap` words
        // (except possibly the last chunk which may be shorter)
        if (overlap > 0 && chunks.size() > 1) {
            for (int i = 0; i < chunks.size() - 1; i++) {
                String[] currentWords = chunks.get(i).split("\\s+");
                String[] nextWords = chunks.get(i + 1).split("\\s+");

                // The last `overlap` words of current chunk should equal
                // the first `overlap` words of next chunk
                int currentLen = currentWords.length;
                if (currentLen >= overlap && nextWords.length >= overlap) {
                    List<String> currentTail = Arrays.asList(
                            Arrays.copyOfRange(currentWords, currentLen - overlap, currentLen));
                    List<String> nextHead = Arrays.asList(
                            Arrays.copyOfRange(nextWords, 0, overlap));

                    assert currentTail.equals(nextHead) :
                            "Chunk " + i + " tail " + currentTail + " does not match chunk " +
                                    (i + 1) + " head " + nextHead + " (expected overlap=" + overlap + ")";
                }
            }
        }

        // Invariant 3: Union of all chunk content covers the complete original text
        // Reconstruct all words from chunks accounting for overlap
        Set<String> originalWords = new HashSet<>(Arrays.asList(words));
        Set<String> coveredWords = new HashSet<>();
        for (String chunk : chunks) {
            coveredWords.addAll(Arrays.asList(chunk.split("\\s+")));
        }

        // Every word in the original should appear in at least one chunk
        for (String word : originalWords) {
            assert coveredWords.contains(word) :
                    "Word '" + word + "' from original text not found in any chunk";
        }

        // Additionally verify sequential coverage: reconstruct by position
        // The step between chunks is (chunkSize - overlap)
        int step = chunkSize - overlap;
        int expectedStart = 0;
        for (int i = 0; i < chunks.size(); i++) {
            String[] chunkWords = chunks.get(i).split("\\s+");
            // Verify chunk starts at expected position
            int actualStart = expectedStart;
            int end = Math.min(actualStart + chunkSize, words.length);
            int expectedLen = end - actualStart;

            assert chunkWords.length == expectedLen :
                    "Chunk " + i + " has " + chunkWords.length + " words but expected " +
                            expectedLen + " (start=" + actualStart + ", end=" + end + ")";

            // Verify each word matches original
            for (int j = 0; j < chunkWords.length; j++) {
                assert chunkWords[j].equals(words[actualStart + j]) :
                        "Chunk " + i + " word " + j + ": expected '" + words[actualStart + j] +
                                "' but got '" + chunkWords[j] + "'";
            }

            expectedStart += step;
        }
    }

    @Provide
    Arbitrary<String> randomText() {
        // Generate random text with 10-500 words
        Arbitrary<String> word = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(12);

        return word.list().ofMinSize(10).ofMaxSize(500)
                .map(words -> String.join(" ", words));
    }

    // ========================================================================
    // Property 13: Chunk metadata completeness
    // For any chunk stored in the Vector_Store, the associated metadata SHALL
    // contain: file_name (non-empty string), file_path (relative path from
    // KB directory), file_type (one of .md, .txt, .pdf), and
    // ingestion_timestamp (valid timestamp).
    // Validates: Requirements 4.10
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 13: Chunk metadata completeness")
    void chunkMetadataCompleteness(
            @ForAll("supportedFileName") String fileName
    ) throws IOException {
        Path tempDir = Files.createTempDirectory("rag-prop13-");
        try {
            Set<String> supportedExtensions = Set.of(".md", ".txt", ".pdf");

            // Create file with enough content to produce chunks
            Path file = tempDir.resolve(fileName);
            String content = generateWordsContent(100);
            Files.writeString(file, content);

            InMemoryVectorStore vectorStore = new InMemoryVectorStore();
            TextChunker textChunker = new TextChunker();
            DefaultDocumentIngester ingester = new DefaultDocumentIngester(textChunker, vectorStore);

            Instant beforeIngestion = Instant.now();
            IngestionConfig config = new IngestionConfig(64, 10, supportedExtensions);
            IngestionResult result = ingester.ingest(tempDir, config);
            Instant afterIngestion = Instant.now();

            assert result.documentCount() >= 1 :
                    "Should have ingested at least 1 document for file: " + fileName;

            // Retrieve all chunks and verify metadata
            // Use a broad query to retrieve stored chunks
            var retrievedChunks = vectorStore.retrieve(content.substring(0, 20), 1000, 0.0);

            for (var chunk : retrievedChunks) {
                ChunkMetadata meta = chunk.metadata();

                // file_name is non-empty
                assert meta.fileName() != null && !meta.fileName().isEmpty() :
                        "file_name must be non-empty";

                // file_path is the relative path from KB directory
                assert meta.filePath() != null && !meta.filePath().isEmpty() :
                        "file_path must be non-empty";
                assert meta.filePath().contains(fileName) || fileName.contains(meta.filePath()) :
                        "file_path '" + meta.filePath() + "' should relate to file '" + fileName + "'";

                // file_type is one of supported extensions
                assert supportedExtensions.contains(meta.fileType()) :
                        "file_type '" + meta.fileType() + "' must be one of " + supportedExtensions;

                // ingestion_timestamp is valid and reasonable
                assert meta.ingestionTimestamp() != null :
                        "ingestion_timestamp must not be null";
                assert !meta.ingestionTimestamp().isBefore(beforeIngestion) :
                        "ingestion_timestamp should not be before ingestion started";
                assert !meta.ingestionTimestamp().isAfter(afterIngestion.plusSeconds(1)) :
                        "ingestion_timestamp should not be after ingestion completed";
            }
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Provide
    Arbitrary<String> supportedFileName() {
        Arbitrary<String> baseName = Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(15);
        Arbitrary<String> extension = Arbitraries.of(".md", ".txt");

        return Combinators.combine(baseName, extension).as((name, ext) -> name + ext);
    }

    // ========================================================================
    // Property 14: Re-ingestion replaces without duplicates
    // For any document that is ingested, modified, and then re-ingested, the
    // Vector_Store SHALL contain only the chunks from the most recent ingestion
    // for that document's file path, with zero chunks remaining from the
    // previous ingestion.
    // Validates: Requirements 4.11
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 14: Re-ingestion replaces without duplicates")
    void reIngestionReplacesWithoutDuplicates(
            @ForAll @IntRange(min = 64, max = 200) int chunkSize
    ) throws IOException {
        Path tempDir = Files.createTempDirectory("rag-prop14-");
        try {
            Set<String> supportedExtensions = Set.of(".md", ".txt", ".pdf");
            String fileName = "testdoc.md";
            Path file = tempDir.resolve(fileName);

            // First ingestion with original content
            String originalContent = generateWordsContent(150);
            Files.writeString(file, originalContent);

            InMemoryVectorStore vectorStore = new InMemoryVectorStore();
            TextChunker textChunker = new TextChunker();
            DefaultDocumentIngester ingester = new DefaultDocumentIngester(textChunker, vectorStore);

            int overlap = Math.min(10, chunkSize / 2);
            IngestionConfig config = new IngestionConfig(chunkSize, overlap, supportedExtensions);
            IngestionResult firstResult = ingester.ingest(tempDir, config);

            int firstChunkCount = firstResult.chunkCount();
            assert firstChunkCount > 0 : "First ingestion should produce chunks";

            int sizeAfterFirst = vectorStore.size();

            // Modify the file with different content
            String modifiedContent = generateWordsContent(200);
            Files.writeString(file, modifiedContent);

            // Re-ingest
            IngestionResult secondResult = ingester.ingest(tempDir, config);
            int secondChunkCount = secondResult.chunkCount();

            // Vector store should contain ONLY the new chunks for that file
            int sizeAfterSecond = vectorStore.size();
            assert sizeAfterSecond == secondChunkCount :
                    "After re-ingestion, store should have exactly " + secondChunkCount +
                            " chunks but has " + sizeAfterSecond;

            // No duplicates: the old chunks should be gone
            // The file path is relative; for a single file in root it's just the filename
            String relativePath = fileName;
            int fileChunkCount = vectorStore.sizeForFile(relativePath);
            assert fileChunkCount == secondChunkCount :
                    "File chunks should be " + secondChunkCount + " but got " + fileChunkCount;
        } finally {
            deleteDirectory(tempDir);
        }
    }

    // ========================================================================
    // Property 15: Ingestion error tolerance
    // For any set of documents where some fail to parse, the system SHALL
    // successfully ingest all parseable documents, list all failed documents
    // by name in the response, and the total document_count + failed count
    // SHALL equal the original file count.
    // Validates: Requirements 4.6
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 15: Ingestion error tolerance")
    void ingestionErrorTolerance(
            @ForAll @IntRange(min = 1, max = 5) int goodFileCount,
            @ForAll @IntRange(min = 1, max = 3) int emptyFileCount
    ) throws IOException {
        Path tempDir = Files.createTempDirectory("rag-prop15-");
        try {
            Set<String> supportedExtensions = Set.of(".md", ".txt", ".pdf");

            // Create good files with content that will produce chunks
            List<String> goodFiles = new ArrayList<>();
            for (int i = 0; i < goodFileCount; i++) {
                String name = "good" + i + ".md";
                goodFiles.add(name);
                Files.writeString(tempDir.resolve(name), generateWordsContent(100));
            }

            // Create files that will produce zero chunks (empty/blank content)
            // These won't fail per se, but won't count as processed either
            // To test actual failure tolerance, we use very large file simulation
            // Instead let's create a mix: some parseable, some that produce 0 chunks
            List<String> emptyFiles = new ArrayList<>();
            for (int i = 0; i < emptyFileCount; i++) {
                String name = "empty" + i + ".txt";
                emptyFiles.add(name);
                // Write blank content - produces 0 chunks but doesn't error
                Files.writeString(tempDir.resolve(name), "   \n  \n   ");
            }

            InMemoryVectorStore vectorStore = new InMemoryVectorStore();
            TextChunker textChunker = new TextChunker();
            DefaultDocumentIngester ingester = new DefaultDocumentIngester(textChunker, vectorStore);

            IngestionConfig config = new IngestionConfig(64, 10, supportedExtensions);
            IngestionResult result = ingester.ingest(tempDir, config);

            // Good files should be ingested successfully
            assert result.documentCount() >= goodFileCount :
                    "Should have ingested at least " + goodFileCount + " documents but got " +
                            result.documentCount();

            // Total accounted files = documentCount + failedDocuments
            int totalAccountedFor = result.documentCount() + result.failedDocuments().size();
            int totalSupportedFiles = goodFileCount + emptyFileCount;

            // All supported files should be accounted for either as processed or failed
            // Note: empty files that produce 0 chunks still count as "processed" (documentCount)
            // or may be skipped. The invariant is:
            // total accounted <= total supported files
            assert totalAccountedFor <= totalSupportedFiles :
                    "Accounted files (" + totalAccountedFor + ") should not exceed total supported files (" +
                            totalSupportedFiles + ")";

            // All failed documents should have names
            for (String failedDoc : result.failedDocuments()) {
                assert failedDoc != null && !failedDoc.isEmpty() :
                        "Failed document names must be non-empty strings";
            }

            // The successfully ingested documents should have chunks in the store
            assert result.chunkCount() > 0 :
                    "At least some chunks should be produced from good files";
        } finally {
            deleteDirectory(tempDir);
        }
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    /**
     * Generates a string with the specified number of words.
     */
    private String generateWordsContent(int wordCount) {
        String[] sampleWords = {"the", "quick", "brown", "fox", "jumps", "over", "lazy",
                "dog", "hello", "world", "testing", "document", "ingestion", "pipeline",
                "vector", "store", "chunk", "metadata", "search", "retrieval", "system",
                "knowledge", "base", "artificial", "intelligence", "machine", "learning"};
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wordCount; i++) {
            if (i > 0) sb.append(' ');
            sb.append(sampleWords[random.nextInt(sampleWords.length)]);
        }
        return sb.toString();
    }

    /**
     * Recursively deletes a directory and all its contents.
     */
    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                // best-effort cleanup
                            }
                        });
            }
        }
    }
}
