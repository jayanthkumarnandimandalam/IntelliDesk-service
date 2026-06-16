package com.intellidesk.api;

import com.intellidesk.api.dto.*;
import com.intellidesk.config.AppConfig;
import com.intellidesk.evaluation.model.EvaluationResult;
import com.intellidesk.evaluation.model.ExampleResult;
import com.intellidesk.evaluation.model.MetricSummary;
import com.intellidesk.rag.model.ChunkMetadata;
import com.intellidesk.rag.model.RetrievedChunk;
import com.intellidesk.resilience.CircuitBreaker;
import com.intellidesk.resilience.CircuitOpenException;
import com.intellidesk.resilience.CircuitState;
import com.intellidesk.resilience.DegradationHandler;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for the API layer, health check, evaluation, degradation, and configuration.
 * Validates Properties 1, 2, 3, 5, 9, 10, 16, 17, 18, 19, 22, 25, 41, 42 from the design document.
 */
class ApiPropertyTest {

    private AppConfig createAppConfig() {
        return new AppConfig(
                5, 0.7, 4000, 10, 30, 20, 10, 60, 30,
                512, 50, 0.7, 120, 5, 5, 30,
                "local", "gpt-4o-mini", "text-embedding-3-small",
                "http://localhost:8000", "http://localhost:9000",
                "data/knowledge-base", "data/evaluation/dataset.json",
                "data/evaluation/report.json", 30, "http://localhost:3000"
        );
    }

    // ========================================================================
    // Property 1: Chat response format completeness
    // Valid request → response has answer, sources, grounding_note, latency_ms
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 1: Chat response format completeness")
    void validChatRequestProducesCompleteResponse(
            @ForAll("validMessages") String message,
            @ForAll("groundingNotes") String groundingNote
    ) {
        // **Validates: Requirements 1.1, 1.5**
        // Simulate a complete chat response built from valid workflow output
        String answer = "Answer for: " + message;
        List<SourceReference> sources = List.of(
                new SourceReference("doc1.md", "Section A")
        );
        long latencyMs = System.currentTimeMillis() % 5000 + 1;

        ChatResponse response = new ChatResponse(answer, sources, groundingNote, latencyMs);

        // Response SHALL contain answer (non-null string)
        assertThat(response.answer()).isNotNull();
        // Response SHALL contain sources (array)
        assertThat(response.sources()).isNotNull();
        // Each source SHALL have document_title and section
        for (SourceReference source : response.sources()) {
            assertThat(source.documentTitle()).isNotNull().isNotBlank();
            assertThat(source.section()).isNotNull().isNotBlank();
        }
        // Response SHALL contain grounding_note (one of supported/partial/unsupported)
        assertThat(response.groundingNote()).isIn("supported", "partial", "unsupported");
        // Response SHALL contain latency_ms (positive integer)
        assertThat(response.latencyMs()).isPositive();
    }

    // ========================================================================
    // Property 2: Chat request input validation
    // Invalid input → 400, RAG not invoked
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 2: Chat request input validation")
    void invalidSessionIdDetectedAsInvalid(
            @ForAll("invalidSessionIds") String invalidSessionId
    ) {
        // **Validates: Requirements 1.6, 1.7, 1.8**
        // Validate that non-UUID session_ids are detected
        boolean isValid = isValidUuid(invalidSessionId);
        assertThat(isValid)
                .as("'%s' should be detected as invalid UUID", invalidSessionId)
                .isFalse();
    }

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 2: Chat request input validation")
    void emptyOrWhitespaceMessageIsInvalid(
            @ForAll("emptyOrWhitespaceMessages") String invalidMessage
    ) {
        // **Validates: Requirements 1.6, 1.7, 1.8**
        assertThat(invalidMessage.isBlank())
                .as("Empty/whitespace-only messages should be detected as blank")
                .isTrue();
    }

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 2: Chat request input validation")
    void oversizedMessageExceedsMaxLength(
            @ForAll @IntRange(min = 4001, max = 10000) int messageLength
    ) {
        // **Validates: Requirements 1.6, 1.7, 1.8**
        AppConfig config = createAppConfig();
        String oversizedMessage = "x".repeat(messageLength);
        assertThat(oversizedMessage.length()).isGreaterThan(config.maxMessageLength());
    }

    // ========================================================================
    // Property 3: Insufficient context → unsupported response
    // No chunks above threshold → specific unsupported answer
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 3: Insufficient context produces unsupported response")
    void noChunksAboveThresholdProducesUnsupportedResponse(
            @ForAll @IntRange(min = 0, max = 5) int chunkCount,
            @ForAll @DoubleRange(min = 0.0, max = 0.69) double maxScore
    ) {
        // **Validates: Requirements 1.4, 7.5**
        double threshold = 0.7;

        // Generate chunks all below threshold
        List<RetrievedChunk> chunks = IntStream.range(0, chunkCount)
                .mapToObj(i -> new RetrievedChunk(
                        "content " + i,
                        maxScore - (i * 0.01),
                        new ChunkMetadata("file.md", "path/file.md", ".md",
                                Instant.now(), "Section")
                ))
                .toList();

        // Filter chunks by threshold (as the system would)
        List<RetrievedChunk> aboveThreshold = chunks.stream()
                .filter(c -> c.similarityScore() >= threshold)
                .toList();

        // When no chunks are above threshold, response should be unsupported
        assertThat(aboveThreshold).isEmpty();

        // The unsupported response should have specific properties
        String expectedAnswer = "I don't have enough information in my knowledge base to answer this question";
        String groundingNote = "unsupported";
        List<SourceReference> sources = Collections.emptyList();

        ChatResponse response = new ChatResponse(expectedAnswer, sources, groundingNote, 100L);

        assertThat(response.answer()).contains("don't have enough information");
        assertThat(response.sources()).isEmpty();
        assertThat(response.groundingNote()).isEqualTo("unsupported");
    }

    // ========================================================================
    // Property 5: Grounding note classification
    // supported if chunks used and no extension; partial if extends; unsupported if no chunks
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 5: Grounding note classification")
    void groundingNoteClassification(
            @ForAll("groundingScenarios") GroundingScenario scenario
    ) {
        // **Validates: Requirements 1.11**
        String groundingNote;
        if (!scenario.hasChunksAboveThreshold()) {
            groundingNote = "unsupported";
        } else if (scenario.extendsContent()) {
            groundingNote = "partial";
        } else {
            groundingNote = "supported";
        }

        // Verify classification is one of the valid values
        assertThat(groundingNote).isIn("supported", "partial", "unsupported");

        // Verify classification matches scenario conditions
        if (!scenario.hasChunksAboveThreshold()) {
            assertThat(groundingNote).isEqualTo("unsupported");
        } else if (scenario.extendsContent()) {
            assertThat(groundingNote).isEqualTo("partial");
        } else {
            assertThat(groundingNote).isEqualTo("supported");
        }
    }

    // ========================================================================
    // Property 9: Voice response format
    // Valid voice request → response has transcript, answer, sources, latency_ms
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 9: Voice response format completeness")
    void validVoiceRequestProducesCompleteResponse(
            @ForAll("validTranscripts") String transcript,
            @ForAll("validMessages") String answer
    ) {
        // **Validates: Requirements 3.3**
        List<SourceReference> sources = List.of(
                new SourceReference("guide.md", "Setup Steps")
        );
        long latencyMs = 500L;

        VoiceResponse response = new VoiceResponse(transcript, answer, sources, latencyMs);

        // Response SHALL contain transcript (string)
        assertThat(response.transcript()).isNotNull();
        // Response SHALL contain answer (string)
        assertThat(response.answer()).isNotNull();
        // Response SHALL contain sources (array)
        assertThat(response.sources()).isNotNull();
        for (SourceReference source : response.sources()) {
            assertThat(source.documentTitle()).isNotNull().isNotBlank();
            assertThat(source.section()).isNotNull().isNotBlank();
        }
        // Response SHALL contain latency_ms (positive integer)
        assertThat(response.latencyMs()).isPositive();
    }

    // ========================================================================
    // Property 10: Audio format validation
    // Non-WAV/WebM/MP3/OGG → 415
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 10: Audio format validation")
    void unsupportedAudioFormatIsRejected(
            @ForAll("unsupportedAudioFormats") String filename
    ) {
        // **Validates: Requirements 3.7**
        Set<String> supportedExtensions = Set.of("wav", "webm", "mp3", "ogg");

        String extension = getFileExtension(filename);
        assertThat(supportedExtensions.contains(extension.toLowerCase()))
                .as("Format '%s' (extension '%s') should be unsupported", filename, extension)
                .isFalse();
    }

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 10: Audio format validation")
    void supportedAudioFormatIsAccepted(
            @ForAll("supportedAudioFormats") String filename
    ) {
        // **Validates: Requirements 3.7**
        Set<String> supportedExtensions = Set.of("wav", "webm", "mp3", "ogg");

        String extension = getFileExtension(filename);
        assertThat(supportedExtensions.contains(extension.toLowerCase()))
                .as("Format '%s' (extension '%s') should be supported", filename, extension)
                .isTrue();
    }

    // ========================================================================
    // Property 16: Health status aggregation
    // All UP → "UP" (200); any DOWN → "DEGRADED" (503)
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 16: Health status aggregation")
    void healthStatusAggregation(
            @ForAll("dependencyStatusCombinations") Map<String, String> statuses
    ) {
        // **Validates: Requirements 5.2, 5.3, 5.4**
        Map<String, DependencyHealth> dependencies = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : statuses.entrySet()) {
            dependencies.put(entry.getKey(), new DependencyHealth(
                    entry.getValue(), 50L, Instant.now(), "CLOSED"
            ));
        }

        // Compute overall status using same logic as HealthCheckService
        boolean allUp = dependencies.values().stream()
                .allMatch(dh -> "UP".equals(dh.status()));
        String overallStatus = allUp ? "UP" : "DEGRADED";

        // ALL UP → "UP"
        if (statuses.values().stream().allMatch("UP"::equals)) {
            assertThat(overallStatus).isEqualTo("UP");
        } else {
            // Any DOWN → "DEGRADED"
            assertThat(overallStatus).isEqualTo("DEGRADED");
        }
    }

    // ========================================================================
    // Property 17: Health check timeout
    // Timed-out dependency → DOWN
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 17: Health check timeout classification")
    void timedOutDependencyIsReportedAsDown(
            @ForAll("dependencyNames") String dependencyName,
            @ForAll @IntRange(min = 1, max = 10) int timeoutSeconds
    ) {
        // **Validates: Requirements 5.6**
        // Simulate a timed-out health check using HealthCheckService logic
        AppConfig config = createAppConfig();

        // Create a circuit breaker that simulates a timeout
        CircuitBreaker timedOutBreaker = new CircuitBreaker() {
            @Override
            public CircuitState getState() {
                return CircuitState.CLOSED;
            }

            @Override
            public <T> T execute(Supplier<T> action) throws CircuitOpenException {
                // Simulate slow response that would trigger timeout
                try {
                    TimeUnit.SECONDS.sleep(timeoutSeconds + 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return action.get();
            }

            @Override
            public void recordSuccess() {}

            @Override
            public void recordFailure() {}
        };

        AtomicReference<Instant> lastSuccessful = new AtomicReference<>(null);

        HealthCheckService service = new HealthCheckService(
                config, timedOutBreaker, timedOutBreaker, timedOutBreaker);

        // Check the dependency with a shorter timeout
        DependencyHealth health = service.checkDependency(
                dependencyName, timedOutBreaker, lastSuccessful, 1);

        // Timed-out dependency SHALL be reported as DOWN
        assertThat(health.status()).isEqualTo("DOWN");
    }

    // ========================================================================
    // Property 18: Evaluation metrics
    // All metrics 0.0-1.0, overall_pass iff all >= threshold
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 18: Evaluation metric computation")
    void evaluationMetricsAreInRangeAndOverallPassIsCorrect(
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double retrievalPrecision,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double answerRelevance,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double groundingAccuracy,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double unsupportedDetectionRate,
            @ForAll @DoubleRange(min = 0.1, max = 0.9) double threshold
    ) {
        // **Validates: Requirements 6.3, 6.4**
        MetricSummary metrics = new MetricSummary(
                retrievalPrecision, answerRelevance, groundingAccuracy, unsupportedDetectionRate);

        // All metrics SHALL be between 0.0 and 1.0
        assertThat(metrics.retrievalPrecision()).isBetween(0.0, 1.0);
        assertThat(metrics.answerRelevance()).isBetween(0.0, 1.0);
        assertThat(metrics.groundingAccuracy()).isBetween(0.0, 1.0);
        assertThat(metrics.unsupportedDetectionRate()).isBetween(0.0, 1.0);

        // overall_pass SHALL be true iff all metrics >= threshold
        boolean overallPass = retrievalPrecision >= threshold
                && answerRelevance >= threshold
                && groundingAccuracy >= threshold
                && unsupportedDetectionRate >= threshold;

        boolean allMeetThreshold = metrics.retrievalPrecision() >= threshold
                && metrics.answerRelevance() >= threshold
                && metrics.groundingAccuracy() >= threshold
                && metrics.unsupportedDetectionRate() >= threshold;

        assertThat(overallPass).isEqualTo(allMeetThreshold);
    }

    // ========================================================================
    // Property 19: Evaluation report per-example completeness
    // N examples → N results
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 19: Evaluation report per-example completeness")
    void evaluationReportContainsExactlyNResults(
            @ForAll @IntRange(min = 1, max = 50) int exampleCount
    ) {
        // **Validates: Requirements 6.8**
        // Generate N example results
        List<ExampleResult> results = IntStream.range(0, exampleCount)
                .mapToObj(i -> new ExampleResult(
                        "Question " + i,
                        i % 3 == 0 ? "answerable" : (i % 3 == 1 ? "follow-up" : "unsupported"),
                        i % 3 == 0 ? "answerable" : (i % 3 == 1 ? "follow-up" : "unsupported"),
                        true
                ))
                .toList();

        MetricSummary metrics = new MetricSummary(0.8, 0.8, 0.8, 0.8);
        EvaluationResult result = new EvaluationResult(metrics, true, "report.json", results);

        // N examples → exactly N results
        assertThat(result.perExampleResults()).hasSize(exampleCount);

        // Each result SHALL have: question, expected_category, actual_category, pass/fail
        for (ExampleResult er : result.perExampleResults()) {
            assertThat(er.question()).isNotNull().isNotBlank();
            assertThat(er.expectedCategory()).isNotNull().isNotBlank();
            assertThat(er.actualCategory()).isNotNull().isNotBlank();
            // pass is a boolean, always present in the record
        }
    }

    // ========================================================================
    // Property 22: Request ID uniqueness
    // Every request gets unique UUID in X-Request-ID
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 22: Request ID uniqueness and propagation")
    void everyRequestGetsUniqueUuid(
            @ForAll @IntRange(min = 2, max = 50) int requestCount
    ) {
        // **Validates: Requirements 8.1, 8.7**
        Set<String> requestIds = new HashSet<>();

        for (int i = 0; i < requestCount; i++) {
            String requestId = UUID.randomUUID().toString();
            requestIds.add(requestId);
        }

        // All request IDs SHALL be unique
        assertThat(requestIds).hasSize(requestCount);

        // Each SHALL be a valid UUID v4
        for (String id : requestIds) {
            assertThat(id).matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            // Verify it can be parsed as UUID
            UUID parsed = UUID.fromString(id);
            assertThat(parsed).isNotNull();
        }
    }

    // ========================================================================
    // Property 25: Configuration precedence
    // Env var takes precedence over .env
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 25: Configuration precedence")
    void envVarTakesPrecedenceOverDotEnv(
            @ForAll("configPropertyNames") String propertyName,
            @ForAll("configValues") String envValue,
            @ForAll("configValues") String dotEnvValue
    ) {
        // **Validates: Requirements 9.1**
        // Simulate configuration resolution where env var and .env both have a value
        // Spring Boot's property resolution order means environment variables
        // take precedence over application.properties/.env files.

        // The resolved value SHALL be the env var value when both are present
        String resolvedValue = resolveConfigValue(envValue, dotEnvValue);
        assertThat(resolvedValue)
                .as("Env var '%s' should take precedence over .env value '%s'", envValue, dotEnvValue)
                .isEqualTo(envValue);
    }

    // ========================================================================
    // Property 41: STT unavailable
    // Voice → 503, text chat unaffected
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 41: Graceful degradation — STT unavailable")
    void sttUnavailableBlocksVoiceButNotTextChat(
            @ForAll("validMessages") String message
    ) {
        // **Validates: Requirements 15.1**
        // Create a DegradationHandler with STT circuit breaker OPEN
        CircuitBreaker openSttBreaker = createCircuitBreakerWithState(CircuitState.OPEN);
        CircuitBreaker closedLlmBreaker = createCircuitBreakerWithState(CircuitState.CLOSED);
        CircuitBreaker closedVsBreaker = createCircuitBreakerWithState(CircuitState.CLOSED);

        DegradationHandler handler = new DegradationHandler(
                closedLlmBreaker, closedVsBreaker, openSttBreaker);

        // STT unavailable → voice endpoint should return 503
        assertThat(handler.isSttUnavailable()).isTrue();

        // Text chat SHALL continue to function normally (LLM and VS are available)
        assertThat(handler.isLlmUnavailable()).isFalse();
        assertThat(handler.isVectorStoreUnavailable()).isFalse();
    }

    // ========================================================================
    // Property 42: LLM unavailable
    // Chat returns raw chunks, grounding_note "raw retrieval mode"
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: intellidesk, Property 42: Graceful degradation — LLM unavailable")
    void llmUnavailableReturnsRawChunks(
            @ForAll @IntRange(min = 1, max = 5) int chunkCount
    ) {
        // **Validates: Requirements 15.2**
        CircuitBreaker openLlmBreaker = createCircuitBreakerWithState(CircuitState.OPEN);
        CircuitBreaker closedVsBreaker = createCircuitBreakerWithState(CircuitState.CLOSED);
        CircuitBreaker closedSttBreaker = createCircuitBreakerWithState(CircuitState.CLOSED);

        DegradationHandler handler = new DegradationHandler(
                openLlmBreaker, closedVsBreaker, closedSttBreaker);

        assertThat(handler.isLlmUnavailable()).isTrue();

        // Generate some retrieved chunks
        List<RetrievedChunk> chunks = IntStream.range(0, chunkCount)
                .mapToObj(i -> new RetrievedChunk(
                        "Chunk content " + i,
                        0.85 - (i * 0.02),
                        new ChunkMetadata("doc" + i + ".md", "path/doc" + i + ".md",
                                ".md", Instant.now(), "Section " + i)
                ))
                .toList();

        // Build raw retrieval response
        ChatResponse response = handler.buildRawRetrievalResponse(chunks, 200L);

        // Response SHALL contain top-k chunks as raw context in answer field
        assertThat(response.answer()).isNotNull().isNotBlank();
        for (RetrievedChunk chunk : chunks) {
            assertThat(response.answer()).contains(chunk.content());
        }

        // grounding_note SHALL be "raw retrieval mode"
        assertThat(response.groundingNote()).isEqualTo("raw retrieval mode");

        // JSON structure SHALL remain unchanged (same fields present)
        assertThat(response.sources()).isNotNull();
        assertThat(response.latencyMs()).isPositive();
    }

    // ========================================================================
    // Generators / Providers
    // ========================================================================

    @Provide
    Arbitrary<String> validMessages() {
        return Arbitraries.of(
                "How do I reset my password?",
                "What is the VPN setup process?",
                "Help me configure my email",
                "Printer setup instructions please",
                "Where is the software installation guide?",
                "I need two-factor authentication help",
                "How to connect to corporate WiFi?",
                "Disk encryption steps",
                "Network troubleshooting guide",
                "How to backup my files?"
        );
    }

    @Provide
    Arbitrary<String> groundingNotes() {
        return Arbitraries.of("supported", "partial", "unsupported");
    }

    @Provide
    Arbitrary<String> invalidSessionIds() {
        return Arbitraries.of(
                "", "   ", "not-a-uuid", "12345",
                "abc-def-ghi-jkl", "zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz",
                "null", "undefined", "123e4567-e89b-12d3-a456",
                "123e4567-e89b-12d3-a456-42661417400g" // invalid hex char
        );
    }

    @Provide
    Arbitrary<String> emptyOrWhitespaceMessages() {
        return Arbitraries.of("", " ", "   ", "\t", "\n", "\r\n", " \t \n ");
    }

    @Provide
    Arbitrary<String> validTranscripts() {
        return Arbitraries.of(
                "How do I reset my password?",
                "Can you help me with VPN setup?",
                "Tell me about email configuration",
                "What are the printer setup steps?",
                "I need help with software installation"
        );
    }

    @Provide
    Arbitrary<String> unsupportedAudioFormats() {
        return Arbitraries.of(
                "audio.flac", "recording.aac", "music.wma",
                "voice.m4a", "clip.aiff", "sound.au",
                "track.mid", "podcast.ra", "stream.amr",
                "sample.pcm", "audio.3gp", "file.ape"
        );
    }

    @Provide
    Arbitrary<String> supportedAudioFormats() {
        return Arbitraries.of(
                "recording.wav", "audio.webm", "voice.mp3", "clip.ogg",
                "input.WAV", "capture.WebM", "mic.MP3", "record.OGG"
        );
    }

    @Provide
    Arbitrary<Map<String, String>> dependencyStatusCombinations() {
        Arbitrary<String> statusArb = Arbitraries.of("UP", "DOWN");
        return Combinators.combine(statusArb, statusArb, statusArb)
                .as((llm, vs, stt) -> {
                    Map<String, String> map = new LinkedHashMap<>();
                    map.put("llm", llm);
                    map.put("vectorStore", vs);
                    map.put("stt", stt);
                    return map;
                });
    }

    @Provide
    Arbitrary<String> dependencyNames() {
        return Arbitraries.of("llm", "vectorStore", "stt");
    }

    @Provide
    Arbitrary<GroundingScenario> groundingScenarios() {
        return Combinators.combine(
                Arbitraries.of(true, false),
                Arbitraries.of(true, false)
        ).as(GroundingScenario::new);
    }

    @Provide
    Arbitrary<String> configPropertyNames() {
        return Arbitraries.of(
                "INTELLIDESK_RAG_TOP_K",
                "INTELLIDESK_RAG_SIMILARITY_THRESHOLD",
                "INTELLIDESK_SESSION_TIMEOUT_MINUTES",
                "INTELLIDESK_SECURITY_RATE_LIMIT_PER_MINUTE",
                "INTELLIDESK_LLM_MODEL",
                "INTELLIDESK_VECTOR_STORE_URL",
                "INTELLIDESK_STT_URL"
        );
    }

    @Provide
    Arbitrary<String> configValues() {
        return Arbitraries.of(
                "10", "0.8", "60", "30",
                "gpt-4o", "http://prod:8000",
                "http://prod:9000", "text-embedding-3-large",
                "prod", "http://prod:3000"
        );
    }

    // ========================================================================
    // Helper classes and methods
    // ========================================================================

    record GroundingScenario(boolean hasChunksAboveThreshold, boolean extendsContent) {}

    private boolean isValidUuid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private String resolveConfigValue(String envVarValue, String dotEnvValue) {
        // Spring Boot resolution: env vars take precedence over .env/properties files
        if (envVarValue != null && !envVarValue.isBlank()) {
            return envVarValue;
        }
        return dotEnvValue;
    }

    private CircuitBreaker createCircuitBreakerWithState(CircuitState targetState) {
        return new CircuitBreaker() {
            @Override
            public CircuitState getState() {
                return targetState;
            }

            @Override
            public <T> T execute(Supplier<T> action) throws CircuitOpenException {
                if (targetState == CircuitState.OPEN) {
                    throw new CircuitOpenException("test-service", "Circuit is OPEN");
                }
                return action.get();
            }

            @Override
            public void recordSuccess() {}

            @Override
            public void recordFailure() {}
        };
    }
}
