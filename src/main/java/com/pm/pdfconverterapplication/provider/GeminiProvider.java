package com.pm.pdfconverterapplication.provider;

import com.pm.pdfconverterapplication.model.SummaryResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Google Gemini implementation of the LLMProvider strategy.
 * This is a placeholder for future Gemini integration.
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini")
public class GeminiProvider implements LLMProvider {

    @Override
    public SummaryResult summarizePdf(MultipartFile file, String summaryLength) throws Exception {
        throw new UnsupportedOperationException("Gemini not yet implemented");
    }

    @Override
    public String validateApiKey() {
        throw new UnsupportedOperationException("Gemini not yet implemented");
    }
}

