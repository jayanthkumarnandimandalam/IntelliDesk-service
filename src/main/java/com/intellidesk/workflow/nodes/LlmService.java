package com.intellidesk.workflow.nodes;

/**
 * Interface for interacting with a Large Language Model service.
 * Implementations may call OpenAI, local models, or return mock responses.
 */
public interface LlmService {

    /**
     * Generates a response for the given prompt.
     *
     * @param prompt the augmented prompt containing context and user query
     * @return the generated answer text
     */
    String generate(String prompt);
}
