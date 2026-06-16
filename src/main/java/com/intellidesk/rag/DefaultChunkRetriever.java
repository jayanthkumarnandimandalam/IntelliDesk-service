package com.intellidesk.rag;

import com.intellidesk.rag.model.RetrievedChunk;
import com.intellidesk.rag.model.ScoredChunk;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Default implementation of {@link ChunkRetriever} that queries a {@link VectorStore}
 * and applies similarity threshold filtering.
 */
@Service
@org.springframework.context.annotation.Primary
public class DefaultChunkRetriever implements ChunkRetriever {

    private final VectorStore vectorStore;

    public DefaultChunkRetriever(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public List<RetrievedChunk> retrieve(String query, int topK, double similarityThreshold) {
        List<ScoredChunk> results = vectorStore.search(query, topK);

        return results.stream()
                .filter(chunk -> chunk.similarityScore() >= similarityThreshold)
                .sorted(Comparator.comparingDouble(ScoredChunk::similarityScore).reversed())
                .limit(topK)
                .map(this::toRetrievedChunk)
                .toList();
    }

    private RetrievedChunk toRetrievedChunk(ScoredChunk scoredChunk) {
        return new RetrievedChunk(
                scoredChunk.content(),
                scoredChunk.similarityScore(),
                scoredChunk.metadata()
        );
    }
}
