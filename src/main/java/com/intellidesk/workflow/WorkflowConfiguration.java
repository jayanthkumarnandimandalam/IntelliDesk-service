package com.intellidesk.workflow;

import com.intellidesk.config.AppConfig;
import com.intellidesk.rag.ChunkRetriever;
import com.intellidesk.resilience.CircuitBreaker;
import com.intellidesk.workflow.nodes.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Spring configuration that assembles workflow node beans into ordered
 * node lists for the text and voice chat pipelines.
 */
@Configuration
public class WorkflowConfiguration {

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    public LlmService llmService() {
        // Fallback stub implementation when no real LLM service is configured
        return prompt -> "I don't have enough information in my knowledge base to answer this question.";
    }

    @Bean
    public SttService sttService() {
        // Stub implementation for local profile; replace with real STT client in production
        return audio -> "";
    }

    @Bean
    public QueryUnderstandingNode queryUnderstandingNode() {
        return new QueryUnderstandingNode();
    }

    @Bean
    public ContextRetrievalNode contextRetrievalNode(
            ChunkRetriever chunkRetriever,
            @Qualifier("vectorStoreCircuitBreaker") CircuitBreaker vectorStoreCb,
            AppConfig appConfig) {
        return new ContextRetrievalNode(chunkRetriever, vectorStoreCb, appConfig);
    }

    @Bean
    public ContextAugmentationNode contextAugmentationNode() {
        return new ContextAugmentationNode();
    }

    @Bean
    public AnswerGenerationNode answerGenerationNode(
            LlmService llmService,
            @Qualifier("llmCircuitBreaker") CircuitBreaker llmCb) {
        return new AnswerGenerationNode(llmService, llmCb);
    }

    @Bean
    public GroundingEvaluationNode groundingEvaluationNode() {
        return new GroundingEvaluationNode();
    }

    @Bean
    public TranscriptionNode transcriptionNode(
            SttService sttService,
            @Qualifier("sttCircuitBreaker") CircuitBreaker sttCb) {
        return new TranscriptionNode(sttService, sttCb);
    }

    /**
     * Text workflow pipeline: QueryUnderstanding → ContextRetrieval →
     * ContextAugmentation → AnswerGeneration → GroundingEvaluation
     */
    @Bean("textWorkflowNodes")
    public List<WorkflowNode> textWorkflowNodes(
            QueryUnderstandingNode queryUnderstandingNode,
            ContextRetrievalNode contextRetrievalNode,
            ContextAugmentationNode contextAugmentationNode,
            AnswerGenerationNode answerGenerationNode,
            GroundingEvaluationNode groundingEvaluationNode) {
        return List.of(
                queryUnderstandingNode,
                contextRetrievalNode,
                contextAugmentationNode,
                answerGenerationNode,
                groundingEvaluationNode
        );
    }

    /**
     * Voice workflow pipeline: same as text pipeline (transcription happens in browser via Web Speech API)
     */
    @Bean("voiceWorkflowNodes")
    public List<WorkflowNode> voiceWorkflowNodes(
            QueryUnderstandingNode queryUnderstandingNode,
            ContextRetrievalNode contextRetrievalNode,
            ContextAugmentationNode contextAugmentationNode,
            AnswerGenerationNode answerGenerationNode,
            GroundingEvaluationNode groundingEvaluationNode) {
        return List.of(
                queryUnderstandingNode,
                contextRetrievalNode,
                contextAugmentationNode,
                answerGenerationNode,
                groundingEvaluationNode
        );
    }
}
