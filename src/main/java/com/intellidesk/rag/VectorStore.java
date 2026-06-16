package com.intellidesk.rag;

import com.intellidesk.rag.model.ScoredChunk;

import java.util.List;

/**
 * Abstraction for vector similarity search operations.
 * Implementations connect to a specific vector database (ChromaDB, Pinecone, etc.).
 */
public interface VectorStore {

    /**
     * Searches the vector store for chunks semantically similar to the given query.
     *
     * @param query the text query to search for
     * @param topK  the maximum number of results to return
     * @return a list of scored chunks ordered by similarity (descending)
     */
    List<ScoredChunk> search(String query, int topK);
}
