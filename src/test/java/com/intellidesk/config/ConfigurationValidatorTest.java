package com.intellidesk.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConfigurationValidator startup validation logic.
 */
class ConfigurationValidatorTest {

    @Test
    @DisplayName("Validation passes for local profile with defaults")
    void validationPassesForLocalWithDefaults() {
        IntellideskProperties props = createDefaultProperties();
        Environment env = mockEnvironment("local");

        ConfigurationValidator validator = new ConfigurationValidator(props, env);

        assertDoesNotThrow(validator::validate);
    }

    @Test
    @DisplayName("Validation fails for dev profile when vector store URL is missing")
    void validationFailsForDevMissingVectorStoreUrl() {
        IntellideskProperties props = createDefaultProperties();
        props.getVectorStore().setUrl(null);
        Environment env = mockEnvironment("dev");

        ConfigurationValidator validator = new ConfigurationValidator(props, env);

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);
        assertTrue(ex.getMessage().contains("VECTOR_STORE_URL"));
    }

    @Test
    @DisplayName("Validation fails for dev profile when STT URL is missing")
    void validationFailsForDevMissingSttUrl() {
        IntellideskProperties props = createDefaultProperties();
        props.getVectorStore().setUrl("http://vector:8000");
        props.getStt().setUrl(null);
        Environment env = mockEnvironment("dev");

        ConfigurationValidator validator = new ConfigurationValidator(props, env);

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);
        assertTrue(ex.getMessage().contains("STT_SERVICE_URL"));
    }

    @Test
    @DisplayName("Validation fails for prod profile when knowledge base dir is missing")
    void validationFailsForProdMissingKnowledgeBaseDir() {
        IntellideskProperties props = createDefaultProperties();
        props.getVectorStore().setUrl("http://vector:8000");
        props.getStt().setUrl("http://stt:9000");
        props.getRag().setKnowledgeBaseDir(null);
        Environment env = mockEnvironment("prod");

        ConfigurationValidator validator = new ConfigurationValidator(props, env);

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);
        assertTrue(ex.getMessage().contains("KNOWLEDGE_BASE_DIR"));
    }

    @Test
    @DisplayName("Validation fails when LLM model is blank")
    void validationFailsWhenLlmModelBlank() {
        IntellideskProperties props = createDefaultProperties();
        props.getLlm().setModel("");
        Environment env = mockEnvironment("local");

        ConfigurationValidator validator = new ConfigurationValidator(props, env);

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);
        assertTrue(ex.getMessage().contains("intellidesk.llm.model"));
    }

    @Test
    @DisplayName("Validation reports all missing properties at once")
    void validationReportsAllMissingProperties() {
        IntellideskProperties props = createDefaultProperties();
        props.getVectorStore().setUrl(null);
        props.getStt().setUrl(null);
        props.getSecurity().setCorsAllowedOrigins(null);
        props.getRag().setKnowledgeBaseDir(null);
        props.getEvaluation().setDatasetPath(null);
        props.getEvaluation().setReportOutputPath(null);
        Environment env = mockEnvironment("prod");

        ConfigurationValidator validator = new ConfigurationValidator(props, env);

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);
        String message = ex.getMessage();
        assertTrue(message.contains("VECTOR_STORE_URL"));
        assertTrue(message.contains("STT_SERVICE_URL"));
        assertTrue(message.contains("CORS_ALLOWED_ORIGINS"));
        assertTrue(message.contains("KNOWLEDGE_BASE_DIR"));
        assertTrue(message.contains("EVALUATION_DATASET_PATH"));
        assertTrue(message.contains("EVALUATION_REPORT_PATH"));
    }

    @Test
    @DisplayName("Validation passes for prod profile with all required properties set")
    void validationPassesForProdWithAllProperties() {
        IntellideskProperties props = createDefaultProperties();
        props.getVectorStore().setUrl("http://vector:8000");
        props.getStt().setUrl("http://stt:9000");
        props.getSecurity().setCorsAllowedOrigins("https://app.intellidesk.com");
        props.getRag().setKnowledgeBaseDir("/opt/kb");
        props.getEvaluation().setDatasetPath("/opt/eval/dataset.json");
        props.getEvaluation().setReportOutputPath("/opt/eval/report.json");
        Environment env = mockEnvironment("prod");

        ConfigurationValidator validator = new ConfigurationValidator(props, env);

        assertDoesNotThrow(validator::validate);
    }

    @Test
    @DisplayName("Default profile is local when no active profiles set")
    void defaultProfileIsLocalWhenEmpty() {
        IntellideskProperties props = createDefaultProperties();
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{});

        ConfigurationValidator validator = new ConfigurationValidator(props, env);

        assertDoesNotThrow(validator::validate);
    }

    private IntellideskProperties createDefaultProperties() {
        IntellideskProperties props = new IntellideskProperties();
        // Defaults are already set in the property classes
        return props;
    }

    private Environment mockEnvironment(String activeProfile) {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{activeProfile});
        return env;
    }
}
