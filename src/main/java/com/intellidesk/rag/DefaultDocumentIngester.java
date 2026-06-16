package com.intellidesk.rag;

import com.intellidesk.rag.model.ChunkMetadata;
import com.intellidesk.rag.model.IngestionConfig;
import com.intellidesk.rag.model.IngestionResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Default implementation of the {@link DocumentIngester} interface.
 * <p>
 * Scans a knowledge base directory for supported file types, splits documents
 * into overlapping chunks, and stores them in a vector store with metadata.
 * </p>
 * <p>
 * Enforces single-concurrent-ingestion semantics using an {@link AtomicBoolean}.
 * </p>
 */
public class DefaultDocumentIngester implements DocumentIngester {

    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024; // 50 MB
    private static final int MIN_CHUNK_SIZE = 64;
    private static final int MAX_CHUNK_SIZE = 2048;

    private final AtomicBoolean ingestionInProgress = new AtomicBoolean(false);
    private final TextChunker textChunker;
    private final InMemoryVectorStore vectorStore;

    public DefaultDocumentIngester(TextChunker textChunker, InMemoryVectorStore vectorStore) {
        this.textChunker = textChunker;
        this.vectorStore = vectorStore;
    }

    /**
     * Returns whether an ingestion is currently in progress.
     */
    public boolean isIngestionInProgress() {
        return ingestionInProgress.get();
    }

    @Override
    public IngestionResult ingest(Path knowledgeBaseDir, IngestionConfig config) {
        // Validate configuration
        validateConfig(config);

        // Check directory existence
        if (!Files.exists(knowledgeBaseDir) || !Files.isDirectory(knowledgeBaseDir)) {
            throw new IngestionException("Knowledge base directory does not exist: " + knowledgeBaseDir, 404);
        }

        // Attempt to acquire ingestion lock
        if (!ingestionInProgress.compareAndSet(false, true)) {
            throw new IngestionException("Ingestion already in progress", 409);
        }

        try {
            return doIngest(knowledgeBaseDir, config);
        } finally {
            ingestionInProgress.set(false);
        }
    }

    private IngestionResult doIngest(Path knowledgeBaseDir, IngestionConfig config) {
        Set<String> supportedExtensions = config.supportedExtensions();
        List<Path> files = listSupportedFiles(knowledgeBaseDir, supportedExtensions);

        if (files.isEmpty()) {
            throw new IngestionException("Knowledge base directory is empty or contains no supported files", 404);
        }

        int totalDocumentCount = 0;
        int totalChunkCount = 0;
        List<String> failedDocuments = new ArrayList<>();

        Instant ingestionTimestamp = Instant.now();

        for (Path file : files) {
            try {
                int chunkCount = processFile(file, knowledgeBaseDir, config, ingestionTimestamp);
                totalDocumentCount++;
                totalChunkCount += chunkCount;
            } catch (Exception e) {
                failedDocuments.add(file.getFileName().toString());
            }
        }

        String status = failedDocuments.isEmpty() ? "completed" : "completed_with_errors";
        return new IngestionResult(totalDocumentCount, totalChunkCount, status, failedDocuments);
    }

    private int processFile(Path file, Path baseDir, IngestionConfig config, Instant ingestionTimestamp)
            throws IOException {
        // Validate file size
        long fileSize = Files.size(file);
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            throw new IOException("File exceeds maximum size of 50MB: " + file.getFileName());
        }

        // Read file content
        String content = readFileContent(file);
        if (content.isBlank()) {
            return 0;
        }

        // Extract section title (first line for md/txt files)
        String sectionTitle = extractSectionTitle(content, file);

        // Chunk the content
        List<String> chunks = textChunker.splitIntoChunks(content, config.chunkSize(), config.chunkOverlap());
        if (chunks.isEmpty()) {
            return 0;
        }

        // Build metadata for each chunk
        String relativePath = baseDir.relativize(file).toString().replace('\\', '/');
        String fileName = file.getFileName().toString();
        String fileType = getFileExtension(fileName);

        List<ChunkMetadata> metadataList = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            metadataList.add(new ChunkMetadata(
                    fileName,
                    relativePath,
                    fileType,
                    ingestionTimestamp,
                    sectionTitle
            ));
        }

        // Re-ingestion: delete old chunks for the same file path, then store new ones
        vectorStore.delete(relativePath);
        vectorStore.store(chunks, metadataList);

        return chunks.size();
    }

    private String readFileContent(Path file) throws IOException {
        String extension = getFileExtension(file.getFileName().toString());
        if (".pdf".equals(extension)) {
            // PDF stub: return empty content (real implementation would use PDFBox)
            return extractPdfText(file);
        }
        return Files.readString(file);
    }

    /**
     * Stub for PDF text extraction. In a real implementation, this would use
     * Apache PDFBox or a similar library to extract text from PDF files.
     */
    private String extractPdfText(Path file) {
        // Stub implementation - PDFs are not fully supported yet
        // In production, this would use Apache PDFBox:
        // PDDocument document = Loader.loadPDF(file.toFile());
        // PDFTextStripper stripper = new PDFTextStripper();
        // return stripper.getText(document);
        return "";
    }

    private String extractSectionTitle(String content, Path file) {
        String extension = getFileExtension(file.getFileName().toString());
        if (".md".equals(extension)) {
            // For markdown files, use the first heading
            String[] lines = content.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#")) {
                    return trimmed.replaceFirst("^#+\\s*", "");
                }
            }
        }
        // Default: use first non-empty line (trimmed to reasonable length)
        String[] lines = content.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
            }
        }
        return file.getFileName().toString();
    }

    private List<Path> listSupportedFiles(Path directory, Set<String> supportedExtensions) {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> {
                        String ext = getFileExtension(p.getFileName().toString());
                        return supportedExtensions.contains(ext);
                    })
                    .forEach(result::add);
        } catch (IOException e) {
            // If we can't walk the directory, return empty list
        }
        return result;
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0) {
            return "";
        }
        return fileName.substring(lastDot).toLowerCase();
    }

    private void validateConfig(IngestionConfig config) {
        if (config.chunkSize() < MIN_CHUNK_SIZE || config.chunkSize() > MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException(
                    "chunkSize must be between " + MIN_CHUNK_SIZE + " and " + MAX_CHUNK_SIZE +
                            ", got: " + config.chunkSize());
        }
        int maxOverlap = config.chunkSize() / 2;
        if (config.chunkOverlap() < 0 || config.chunkOverlap() > maxOverlap) {
            throw new IllegalArgumentException(
                    "chunkOverlap must be between 0 and " + maxOverlap +
                            " (50% of chunkSize), got: " + config.chunkOverlap());
        }
    }
}
