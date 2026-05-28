package com.pm.pdfconverterapplication.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for AI provider settings.
 * Maps properties from ai.* namespace with support for @ConfigurationProperties naming conventions.
 */
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /**
     * The AI provider to use: "openai" or "gemini".
     * Defaults to "openai".
     */
    private String provider = "openai";

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}


