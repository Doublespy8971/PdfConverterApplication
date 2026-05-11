package com.pm.pdfconverterapplication.controller;

import com.google.gson.Gson;
import com.pm.pdfconverterapplication.model.SummaryResult;
import com.pm.pdfconverterapplication.provider.LLMProvider;
import com.pm.pdfconverterapplication.service.AsyncSummarizationWorker;
import com.pm.pdfconverterapplication.service.TaskRegistryService;
import com.pm.pdfconverterapplication.service.TaskRegistryService.TaskStatus;
import com.pm.pdfconverterapplication.util.FileNameUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AIController {

    private final LLMProvider llmProvider;
    private final AsyncSummarizationWorker asyncSummarizationWorker;
    private final TaskRegistryService taskRegistryService;
    private final Gson gson = new Gson();

    public AIController(LLMProvider llmProvider, AsyncSummarizationWorker asyncSummarizationWorker, TaskRegistryService taskRegistryService) {
        this.llmProvider = llmProvider;
        this.asyncSummarizationWorker = asyncSummarizationWorker;
        this.taskRegistryService = taskRegistryService;
    }

    @PostMapping("/summarize")
    public ResponseEntity<?> summarizePdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "length", defaultValue = "medium") String summaryLength) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File is empty");
            }

            String extension = FileNameUtils.getSafeExtension(file.getOriginalFilename());
            if (!extension.equals("pdf")) {
                return ResponseEntity.badRequest().body("Only PDF files are supported for summarization");
            }

            String taskId = taskRegistryService.initiateTask();

            String safeFilename = FileNameUtils.sanitizeFileName(file.getOriginalFilename());
            String tempDir = System.getProperty("java.io.tmpdir") + java.io.File.separator + "ai_" + taskId;
            Files.createDirectories(Path.of(tempDir));
            Path filePath = Path.of(tempDir, safeFilename);
            file.transferTo(filePath);

            asyncSummarizationWorker.summarizeAsync(filePath.toString(), safeFilename, summaryLength, taskId);

            Map<String, Object> response = new HashMap<>();
            response.put("taskId", taskId);
            response.put("status", "PENDING");
            response.put("message", "AI summarization initiated");
            return ResponseEntity.accepted().body(response);

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

    @GetMapping("/status/{taskId}")
    public ResponseEntity<?> getSummaryTaskStatus(@PathVariable String taskId) {
        if (!taskRegistryService.taskExists(taskId)) {
            return ResponseEntity.notFound().build();
        }

        TaskStatus task = taskRegistryService.getTask(taskId);
        Map<String, Object> response = new HashMap<>();
        response.put("taskId", taskId);
        response.put("status", task.getStatus());
        response.put("errorMessage", task.getErrorMessage());
        response.put("createdAt", task.getCreatedAt());
        response.put("updatedAt", task.getUpdatedAt());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/result/{taskId}")
    public ResponseEntity<?> getSummaryResult(@PathVariable String taskId) {
        if (!taskRegistryService.taskExists(taskId)) {
            return ResponseEntity.notFound().build();
        }

        TaskStatus task = taskRegistryService.getTask(taskId);
        if ("PENDING".equals(task.getStatus()) || "PROCESSING".equals(task.getStatus())) {
            Map<String, Object> response = new HashMap<>();
            response.put("taskId", taskId);
            response.put("status", task.getStatus());
            response.put("message", "Summarization is still processing. Please try again shortly.");
            return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
        }

        if ("FAILED".equals(task.getStatus())) {
            Map<String, Object> response = new HashMap<>();
            response.put("taskId", taskId);
            response.put("status", "FAILED");
            response.put("errorMessage", task.getErrorMessage());
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        if ("COMPLETED".equals(task.getStatus())) {
            String payload = new String(task.getResultContent(), StandardCharsets.UTF_8);
            SummaryResult result = gson.fromJson(payload, SummaryResult.class);
            taskRegistryService.removeTask(taskId);
            return ResponseEntity.ok(result);
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unknown task status: " + task.getStatus());
    }

    public record ApiStatusResponse(boolean ready, String message) {}
}


