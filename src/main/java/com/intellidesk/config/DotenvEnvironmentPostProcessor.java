package com.intellidesk.config;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads configuration from a .env file (if present) into the Spring Environment
 * with lower precedence than actual environment variables.
 *
 * This ensures that environment variables always take precedence over .env file values,
 * fulfilling Requirement 9.1.
 *
 * For the "prod" profile, .env loading is skipped entirely - secrets must come
 * from environment variables only (Requirement 9.3).
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String DOTENV_PROPERTY_SOURCE_NAME = "dotenvProperties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Determine active profile from env var (before Spring profile resolution)
        String appProfile = System.getenv("APP_PROFILE");
        if (appProfile == null || appProfile.isBlank()) {
            appProfile = environment.getProperty("APP_PROFILE", "local");
        }

        // Do not load .env file in prod profile - secrets must come from env vars only
        if ("prod".equals(appProfile)) {
            return;
        }

        // Only load if .env file exists
        Path dotenvPath = Path.of(".env");
        if (!Files.exists(dotenvPath)) {
            return;
        }

        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        Map<String, Object> dotenvMap = new HashMap<>();
        for (DotenvEntry entry : dotenv.entries()) {
            // Only add if NOT already set as a real environment variable
            // This ensures env vars take precedence over .env values
            if (System.getenv(entry.getKey()) == null) {
                dotenvMap.put(entry.getKey(), entry.getValue());
            }
        }

        if (!dotenvMap.isEmpty()) {
            MapPropertySource propertySource = new MapPropertySource(DOTENV_PROPERTY_SOURCE_NAME, dotenvMap);
            // Add at the end (lowest precedence) so env vars and system properties win
            environment.getPropertySources().addLast(propertySource);
        }
    }
}
