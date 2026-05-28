package com.pm.pdfconverterapplication.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for OpenAI provider settings.
 * Maps properties from openai.* namespace with support for @ConfigurationProperties naming conventions.
 */
@Component
@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {

    /**
     * OpenAI API key for authentication.
     * Property name: openai.api-key
     * Obtain from: https://platform.openai.com/api-keys
     */
    private String apiKey = "";

    /**
     * OpenAI model to use for API calls (e.g., "gpt-3.5-turbo", "gpt-4").
     * Property name: openai.model
     */
    private String model = "gpt-3.5-turbo";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}


