package com.example.walletledger.adapters.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Objects;

@Configuration
public class CorsPolicyConfiguration implements WebMvcConfigurer {

    private static final String ALL_API_PATHS = "/**";

    private final CorsProperties corsProperties;

    public CorsPolicyConfiguration(CorsProperties corsProperties) {
        this.corsProperties = Objects.requireNonNull(corsProperties, "corsProperties");
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry corsRegistry) {
        corsRegistry.addMapping(ALL_API_PATHS)
                .allowedOrigins(toArray(corsProperties.allowedOrigins()))
                .allowedMethods(toArray(corsProperties.allowedMethods()))
                .allowedHeaders(toArray(corsProperties.allowedHeaders()))
                .exposedHeaders(toArray(corsProperties.exposedHeaders()))
                .allowCredentials(false)
                .maxAge(corsProperties.maxAge().toSeconds());
    }

    private String[] toArray(List<String> values) {
        return values.toArray(String[]::new);
    }
}
