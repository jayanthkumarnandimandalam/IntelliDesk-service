package com.intellidesk.config;

/**
 * Configuration properties for the LLM service.
 */
public class LlmProperties {

    private String model = "llama-3.3-70b-versatile";
    private int timeoutSeconds = 30;
    private String baseUrl = "https://api.groq.com/openai/v1";

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
