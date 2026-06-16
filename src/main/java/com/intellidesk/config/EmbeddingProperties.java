package com.intellidesk.config;

/**
 * Configuration properties for the embedding model.
 */
public class EmbeddingProperties {

    private String model = "text-embedding-3-small";

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
