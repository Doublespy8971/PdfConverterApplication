package com.pm.pdfconverterapplication.model;

/**
 * Represents the result of a PDF summarization operation.
 */
public record SummaryResult(String summary, String originalLength, String summaryLength) {}

