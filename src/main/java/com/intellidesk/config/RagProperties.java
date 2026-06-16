package com.intellidesk.config;

/**
 * Configuration properties for the RAG pipeline.
 */
public class RagProperties {

    private int topK = 5;
    private double similarityThreshold = 0.7;
    private int chunkSize = 512;
    private int chunkOverlap = 50;
    private String knowledgeBaseDir = "./data/knowledge-base";

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public String getKnowledgeBaseDir() {
        return knowledgeBaseDir;
    }

    public void setKnowledgeBaseDir(String knowledgeBaseDir) {
        this.knowledgeBaseDir = knowledgeBaseDir;
    }
}
