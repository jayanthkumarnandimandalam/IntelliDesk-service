package com.intellidesk.rag;

import com.intellidesk.rag.model.ChunkMetadata;
import com.intellidesk.rag.model.RetrievedChunk;
import com.intellidesk.rag.model.ScoredChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory vector store implementation for local development.
 * Stores chunks with metadata without real embedding generation.
 * Uses simple keyword-based similarity as a placeholder for semantic search.
 */
@Component
public class InMemoryVectorStore implements ChunkRetriever, VectorStore {

    /**
     * Internal representation of a stored chunk.
     */
    private record StoredChunk(String content, ChunkMetadata metadata) {}

    /**
     * Map from filePath to the list of stored chunks for that file.
     */
    private final Map<String, List<StoredChunk>> chunksByFilePath = new ConcurrentHashMap<>();

    /**
     * Stores a list of chunks with their associated metadata.
     * If chunks for the same filePath already exist, they are replaced.
     *
     * @param chunks   the text content of each chunk
     * @param metadata the metadata for each chunk (must be same size as chunks)
     */
    public void store(List<String> chunks, List<ChunkMetadata> metadata) {
        if (chunks == null || metadata == null || chunks.size() != metadata.size()) {
            throw new IllegalArgumentException("chunks and metadata must be non-null and same size");
        }

        if (chunks.isEmpty()) {
            return;
        }

        String filePath = metadata.get(0).filePath();
        List<StoredChunk> storedChunks = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            storedChunks.add(new StoredChunk(chunks.get(i), metadata.get(i)));
        }
        chunksByFilePath.put(filePath, storedChunks);
    }

    /**
     * Deletes all chunks associated with the given file path.
     *
     * @param filePath the file path whose chunks should be removed
     */
    public void delete(String filePath) {
        chunksByFilePath.remove(filePath);
    }

    /**
     * Retrieves chunks matching the query using simple word-overlap similarity.
     * This is a placeholder implementation; real implementations would use cosine similarity
     * on vector embeddings.
     *
     * @param query              the search query
     * @param topK               maximum number of results to return
     * @param similarityThreshold minimum similarity score (0.0 to 1.0)
     * @return list of matching chunks sorted by similarity descending
     */
    @Override
    public List<RetrievedChunk> retrieve(String query, int topK, double similarityThreshold) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String[] queryWords = query.toLowerCase().split("\\s+");

        List<RetrievedChunk> results = new ArrayList<>();

        for (List<StoredChunk> fileChunks : chunksByFilePath.values()) {
            for (StoredChunk chunk : fileChunks) {
                double score = computeSimilarity(queryWords, chunk.content().toLowerCase());
                if (score >= similarityThreshold) {
                    results.add(new RetrievedChunk(chunk.content(), score, chunk.metadata()));
                }
            }
        }

        return results.stream()
                .sorted((a, b) -> Double.compare(b.similarityScore(), a.similarityScore()))
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * Returns total number of stored chunks across all files.
     */
    public int size() {
        return chunksByFilePath.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * Returns the number of chunks stored for a specific file path.
     */
    public int sizeForFile(String filePath) {
        List<StoredChunk> chunks = chunksByFilePath.get(filePath);
        return chunks == null ? 0 : chunks.size();
    }

    /**
     * Checks if any chunks exist for the given file path.
     */
    public boolean containsFile(String filePath) {
        return chunksByFilePath.containsKey(filePath);
    }

    /**
     * Simple word-overlap similarity: proportion of query words found in the chunk.
     */
    private double computeSimilarity(String[] queryWords, String chunkContent) {
        if (queryWords.length == 0) {
            return 0.0;
        }
        int matches = 0;
        for (String word : queryWords) {
            if (chunkContent.contains(word)) {
                matches++;
            }
        }
        return (double) matches / queryWords.length;
    }

    /**
     * Searches the vector store for chunks semantically similar to the given query.
     * Required by the {@link VectorStore} interface.
     */
    @Override
    public List<ScoredChunk> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String[] queryWords = query.toLowerCase().split("\\s+");
        List<ScoredChunk> results = new ArrayList<>();

        for (List<StoredChunk> fileChunks : chunksByFilePath.values()) {
            for (StoredChunk chunk : fileChunks) {
                double score = computeSimilarity(queryWords, chunk.content().toLowerCase());
                if (score > 0.0) {
                    results.add(new ScoredChunk(chunk.content(), score, chunk.metadata()));
                }
            }
        }

        return results.stream()
                .sorted((a, b) -> Double.compare(b.similarityScore(), a.similarityScore()))
                .limit(topK)
                .collect(Collectors.toList());
    }
}
