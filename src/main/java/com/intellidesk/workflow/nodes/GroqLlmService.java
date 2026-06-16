package com.intellidesk.workflow.nodes;

import com.intellidesk.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

/**
 * LLM service implementation that calls the Groq API (OpenAI-compatible format).
 * Uses RestClient with SSL verification disabled to work behind corporate proxies.
 * Configured via GROQ_API_KEY environment variable and application.yml properties.
 */
@Service
@Primary
public class GroqLlmService implements LlmService {

    private static final Logger logger = LoggerFactory.getLogger(GroqLlmService.class);

    private final RestClient restClient;
    private final String model;

    public GroqLlmService(
            @Value("${intellidesk.llm.base-url:https://api.groq.com/openai/v1}") String baseUrl,
            @Value("${GROQ_API_KEY:}") String apiKey,
            AppConfig appConfig) {

        if (apiKey == null || apiKey.isBlank()) {
            logger.warn("GROQ_API_KEY is not set. LLM calls will fail until configured.");
        }

        this.model = appConfig.llmModel();

        // Create an SSL-bypassing HttpClient for corporate proxy environments
        HttpClient httpClient = createTrustAllHttpClient();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();

        logger.info("GroqLlmService initialized with model={}, baseUrl={} (SSL verification disabled)", model, baseUrl);
    }

    /**
     * Creates an HttpClient that trusts all SSL certificates.
     * This is needed in corporate proxy environments where the proxy
     * intercepts HTTPS with its own certificate.
     */
    private static HttpClient createTrustAllHttpClient() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSL-bypassing HttpClient", e);
        }
    }

    @Override
    public String generate(String prompt) {
        logger.debug("Generating answer via Groq API, model={}, prompt length={}", model, prompt.length());

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.3,
                "max_tokens", 1024
        );

        try {
            Map<?, ?> response = restClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new RuntimeException("Groq API returned null response");
            }

            // Parse the OpenAI-compatible response format
            List<?> choices = (List<?>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("Groq API returned no choices");
            }

            Map<?, ?> firstChoice = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) firstChoice.get("message");
            String content = (String) message.get("content");

            logger.debug("Groq API response received, answer length={}", content != null ? content.length() : 0);
            return content;

        } catch (Exception e) {
            logger.error("Groq API call failed: {}", e.getMessage(), e);
            throw new RuntimeException("LLM generation failed: " + e.getMessage(), e);
        }
    }
}
