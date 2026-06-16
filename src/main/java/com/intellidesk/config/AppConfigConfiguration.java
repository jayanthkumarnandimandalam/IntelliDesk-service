package com.intellidesk.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Spring configuration class that enables binding of IntellideskProperties
 * and produces the immutable AppConfig bean for injection across the application.
 */
@Configuration
@EnableConfigurationProperties(IntellideskProperties.class)
public class AppConfigConfiguration {

    @Bean
    public AppConfig appConfig(IntellideskProperties properties, Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        String activeProfile = (activeProfiles.length > 0) ? activeProfiles[0] : "local";
        return AppConfig.from(properties, activeProfile);
    }
}
