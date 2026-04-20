package com.pm.pdfconverterapplication.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AIService {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.model}")
    private String model;

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Gson gson = new Gson();

    public record SummaryResult(String summary, String originalLength, String summaryLength) {}

    public SummaryResult summarizePdf(MultipartFile file, String summaryLength) throws Exception {
        // Check if API key is configured
        if (apiKey == null || apiKey.isEmpty() || apiKey.contains("add-your")) {
            throw new IllegalArgumentException("OpenAI API key is not configured. Please add your API key to application.properties");
        }

        // Extract text from PDF
        byte[] fileContent = file.getBytes();
        String pdfText;
        try (PDDocument document = PDDocument.load(fileContent)) {
            PDFTextStripper stripper = new PDFTextStripper();
            pdfText = stripper.getText(document);
        }

        if (pdfText.trim().isEmpty()) {
            throw new IllegalArgumentException("PDF document contains no text to summarize");
        }

        // Limit text to avoid token limits (approx 3000 words)
        String truncatedText = truncateText(pdfText, 3000);
        int originalWordCount = pdfText.split("\\s+").length;

        // Create OpenAI summary
        String summary = generateSummaryWithOpenAI(truncatedText, summaryLength);
        int summaryWordCount = summary.split("\\s+").length;

        return new SummaryResult(summary, originalWordCount + " words", summaryWordCount + " words");
    }

    private String truncateText(String text, int maxWords) {
        String[] words = text.split("\\s+");
        if (words.length <= maxWords) {
            return text;
        }

        StringBuilder truncated = new StringBuilder();
        for (int i = 0; i < maxWords; i++) {
            truncated.append(words[i]).append(" ");
        }
        return truncated.toString();
    }

    private String generateSummaryWithOpenAI(String text, String summaryLength) throws Exception {
        String lengthInstruction = switch (summaryLength.toLowerCase()) {
            case "short" -> "Provide a concise 2-3 sentence summary.";
            case "long" -> "Provide a detailed summary with 5-7 sentences, covering main points.";
            default -> "Provide a balanced 3-4 sentence summary.";
        };

        String prompt = "Please summarize the following PDF content. " + lengthInstruction +
                "\n\nContent:\n" + text;

        // Build request body
        JsonObject messageObj = new JsonObject();
        messageObj.addProperty("role", "user");
        messageObj.addProperty("content", prompt);

        JsonArray messagesArray = new JsonArray();
        messagesArray.add(messageObj);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.add("messages", messagesArray);
        requestBody.addProperty("max_tokens", 500);
        requestBody.addProperty("temperature", 0.5);

        String jsonRequest = gson.toJson(requestBody);

        // Make HTTP request
        Request request = new Request.Builder()
                .url(OPENAI_API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonRequest, MediaType.get("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                throw new RuntimeException("OpenAI API error: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body().string();
            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);

            String summary = responseJson
                    .getAsJsonArray("choices")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content")
                    .getAsString();

            return summary.trim();
        }
    }

    public String validateApiKey() {
        if (apiKey == null || apiKey.isEmpty() || apiKey.contains("add-your")) {
            return "API key not configured";
        }
        return "API key configured";
    }
}

