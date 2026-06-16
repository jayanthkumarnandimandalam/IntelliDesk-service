package com.intellidesk.rag;

import com.intellidesk.rag.model.RetrievedChunk;

import java.util.List;

/**
 * Interface for retrieving relevant chunks from the vector store.
 */
public interface ChunkRetriever {

    /**
     * Retrieves the most relevant chunks for the given query.
     *
     * @param query the search query
     * @param topK the maximum number of chunks to retrieve
     * @param similarityThreshold the minimum similarity score for a chunk to be included
     * @return a list of retrieved chunks sorted by similarity score descending
     */
    List<RetrievedChunk> retrieve(String query, int topK, double similarityThreshold);
}
