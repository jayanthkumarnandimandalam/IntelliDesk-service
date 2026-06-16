package com.intellidesk.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellidesk.config.AppConfig;
import com.intellidesk.evaluation.DefaultEvaluationRunner;
import com.intellidesk.evaluation.EvaluationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration providing RAG pipeline beans: TextChunker,
 * DocumentIngester, and EvaluationRunner.
 */
@Configuration
public class RagConfiguration {

    @Bean
    public TextChunker textChunker() {
        return new TextChunker();
    }

    @Bean
    public DefaultDocumentIngester documentIngester(TextChunker textChunker,
                                                   InMemoryVectorStore vectorStore) {
        return new DefaultDocumentIngester(textChunker, vectorStore);
    }

    @Bean
    public EvaluationRunner evaluationRunner(AppConfig appConfig,
                                            ChunkRetriever chunkRetriever,
                                            ObjectMapper objectMapper,
                                            InMemoryVectorStore vectorStore) {
        return new DefaultEvaluationRunner(
                appConfig, chunkRetriever, objectMapper,
                () -> vectorStore.size() > 0);
    }
}
