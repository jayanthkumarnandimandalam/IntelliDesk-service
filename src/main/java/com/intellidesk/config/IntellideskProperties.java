package com.intellidesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Root configuration properties bound to the "intellidesk" prefix in application.yml.
 * All configurable properties for IntelliDesk are nested under this class.
 */
@ConfigurationProperties(prefix = "intellidesk")
public class IntellideskProperties {

    @NestedConfigurationProperty
    private LlmProperties llm = new LlmProperties();

    @NestedConfigurationProperty
    private EmbeddingProperties embedding = new EmbeddingProperties();

    @NestedConfigurationProperty
    private VectorStoreProperties vectorStore = new VectorStoreProperties();

    @NestedConfigurationProperty
    private SttProperties stt = new SttProperties();

    @NestedConfigurationProperty
    private RagProperties rag = new RagProperties();

    @NestedConfigurationProperty
    private SessionProperties session = new SessionProperties();

    @NestedConfigurationProperty
    private SecurityProperties security = new SecurityProperties();

    @NestedConfigurationProperty
    private EvaluationProperties evaluation = new EvaluationProperties();

    @NestedConfigurationProperty
    private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

    @NestedConfigurationProperty
    private HealthProperties health = new HealthProperties();

    @NestedConfigurationProperty
    private WorkflowProperties workflow = new WorkflowProperties();

    // Getters and setters

    public LlmProperties getLlm() {
        return llm;
    }

    public void setLlm(LlmProperties llm) {
        this.llm = llm;
    }

    public EmbeddingProperties getEmbedding() {
        return embedding;
    }

    public void setEmbedding(EmbeddingProperties embedding) {
        this.embedding = embedding;
    }

    public VectorStoreProperties getVectorStore() {
        return vectorStore;
    }

    public void setVectorStore(VectorStoreProperties vectorStore) {
        this.vectorStore = vectorStore;
    }

    public SttProperties getStt() {
        return stt;
    }

    public void setStt(SttProperties stt) {
        this.stt = stt;
    }

    public RagProperties getRag() {
        return rag;
    }

    public void setRag(RagProperties rag) {
        this.rag = rag;
    }

    public SessionProperties getSession() {
        return session;
    }

    public void setSession(SessionProperties session) {
        this.session = session;
    }

    public SecurityProperties getSecurity() {
        return security;
    }

    public void setSecurity(SecurityProperties security) {
        this.security = security;
    }

    public EvaluationProperties getEvaluation() {
        return evaluation;
    }

    public void setEvaluation(EvaluationProperties evaluation) {
        this.evaluation = evaluation;
    }

    public CircuitBreakerProperties getCircuitBreaker() {
        return circuitBreaker;
    }

    public void setCircuitBreaker(CircuitBreakerProperties circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public HealthProperties getHealth() {
        return health;
    }

    public void setHealth(HealthProperties health) {
        this.health = health;
    }

    public WorkflowProperties getWorkflow() {
        return workflow;
    }

    public void setWorkflow(WorkflowProperties workflow) {
        this.workflow = workflow;
    }
}
