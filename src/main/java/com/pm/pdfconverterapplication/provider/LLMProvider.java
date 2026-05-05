package com.pm.pdfconverterapplication.provider;

import com.pm.pdfconverterapplication.model.SummaryResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * Strategy interface for different LLM (Large Language Model) providers.
 * Allows switching between different AI services (OpenAI, Gemini, etc.)
 */
public interface LLMProvider {

    /**
     * Summarizes a PDF document using the LLM provider.
     *
     * @param file            The PDF file to summarize
     * @param summaryLength   The desired summary length (short, medium, long)
     * @return SummaryResult containing the summary and metadata
     * @throws Exception if the summarization fails
     */
    SummaryResult summarizePdf(MultipartFile file, String summaryLength) throws Exception;

    /**
     * Validates if the API key is properly configured for this provider.
     *
     * @return A string describing the API key status
     */
    String validateApiKey();
}

