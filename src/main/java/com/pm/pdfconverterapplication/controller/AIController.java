package com.pm.pdfconverterapplication.controller;

import com.pm.pdfconverterapplication.model.SummaryResult;
import com.pm.pdfconverterapplication.provider.LLMProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai")
public class AIController {

    private final LLMProvider llmProvider;

    public AIController(LLMProvider llmProvider) {
        this.llmProvider = llmProvider;
    }

    @PostMapping("/summarize")
    public ResponseEntity<?> summarizePdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "length", defaultValue = "medium") String summaryLength) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File is empty");
            }

            String extension = getFileExtension(file.getOriginalFilename());
            if (!extension.equals("pdf")) {
                return ResponseEntity.badRequest().body("Only PDF files are supported for summarization");
            }

            SummaryResult result = llmProvider.summarizePdf(file, summaryLength);
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Summarization failed: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> checkApiStatus() {
        try {
            String status = llmProvider.validateApiKey();
            if (status.contains("not configured")) {
                return ResponseEntity.ok(new ApiStatusResponse(false, "LLM Provider API key not configured. Please add your key to application.properties"));
            }
            return ResponseEntity.ok(new ApiStatusResponse(true, "LLM Provider API is ready"));
        } catch (Exception e) {
            return ResponseEntity.ok(new ApiStatusResponse(false, "Error checking API status: " + e.getMessage()));
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    public record ApiStatusResponse(boolean ready, String message) {}
}



