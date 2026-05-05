package com.pm.pdfconverterapplication.controller;

import com.pm.pdfconverterapplication.service.AsyncConversionWorker;
import com.pm.pdfconverterapplication.service.TaskRegistryService;
import com.pm.pdfconverterapplication.service.TaskRegistryService.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for file conversion operations.
 * Uses asynchronous processing to prevent HTTP timeouts on long-running conversions.
 */
@RestController
@RequestMapping("/api/convert")
@CrossOrigin(origins = "*")
public class ConverterController {

    private static final Logger logger = LoggerFactory.getLogger(ConverterController.class);

    private final AsyncConversionWorker asyncConversionWorker;
    private final TaskRegistryService taskRegistryService;

    public ConverterController(AsyncConversionWorker asyncConversionWorker, TaskRegistryService taskRegistryService) {
        this.asyncConversionWorker = asyncConversionWorker;
        this.taskRegistryService = taskRegistryService;
    }

    /**
     * Initiates a batch conversion asynchronously.
     * Returns HTTP 202 Accepted with task ID.
     */
    @PostMapping("/batch/{tool}")
    public ResponseEntity<?> batchConvertFiles(@PathVariable String tool, @RequestParam("files") MultipartFile[] files) {
        try {
            if (files.length == 0) {
                return ResponseEntity.badRequest().body("Please upload at least one file");
            }

            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    return ResponseEntity.badRequest().body("One or more files are empty");
                }
            }

            // Initiate a task
            String taskId = taskRegistryService.initiateTask();
            logger.info("Batch conversion initiated - Task: {}, Tool: {}, Files: {}", taskId, tool, files.length);

            // Extract file bytes
            byte[][] filesBytes = new byte[files.length][];
            String[] filenames = new String[files.length];
            for (int i = 0; i < files.length; i++) {
                filesBytes[i] = files[i].getBytes();
                filenames[i] = files[i].getOriginalFilename();
            }

            // Start async worker
            asyncConversionWorker.batchConvertAsync(filesBytes, filenames, tool, taskId);

            // Return 202 Accepted with task ID
            Map<String, Object> response = new HashMap<>();
            response.put("taskId", taskId);
            response.put("status", "PENDING");
            response.put("message", "Batch conversion processing initiated");
            return ResponseEntity.accepted().body(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error initiating batch conversion", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to initiate batch conversion: " + e.getMessage());
        }
    }

    /**
     * Initiates a single file conversion asynchronously.
     * Returns HTTP 202 Accepted with task ID.
     */
    @PostMapping("/{tool}")
    public ResponseEntity<?> convertFile(@PathVariable String tool, @RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File is empty");
            }

            // Initiate a task
            String taskId = taskRegistryService.initiateTask();
            logger.info("Conversion initiated - Task: {}, Tool: {}, File: {}", taskId, tool, file.getOriginalFilename());

            // Extract file bytes
            byte[] fileBytes = file.getBytes();
            String originalFilename = file.getOriginalFilename();

            // Start async worker
            asyncConversionWorker.convertFileAsync(fileBytes, originalFilename, tool, taskId);

            // Return 202 Accepted with task ID
            Map<String, Object> response = new HashMap<>();
            response.put("taskId", taskId);
            response.put("status", "PENDING");
            response.put("message", "Conversion processing initiated");
            return ResponseEntity.accepted().body(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error initiating conversion", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to initiate conversion: " + e.getMessage());
        }
    }

    /**
     * Initiates a PDF merge operation asynchronously.
     * Returns HTTP 202 Accepted with task ID.
     */
    @PostMapping("/merge-pdf")
    public ResponseEntity<?> mergePdfFiles(@RequestParam("files") MultipartFile[] files) {
        try {
            if (files.length < 2) {
                return ResponseEntity.badRequest().body("Please upload at least 2 PDF files to merge");
            }

            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    return ResponseEntity.badRequest().body("One or more files are empty");
                }
            }

            // Initiate a task
            String taskId = taskRegistryService.initiateTask();
            logger.info("PDF merge initiated - Task: {}, Files: {}", taskId, files.length);

            // Extract file bytes
            byte[][] filesBytes = new byte[files.length][];
            String[] filenames = new String[files.length];
            for (int i = 0; i < files.length; i++) {
                filesBytes[i] = files[i].getBytes();
                filenames[i] = files[i].getOriginalFilename();
            }

            // Start async worker
            asyncConversionWorker.mergePdfsAsync(filesBytes, filenames, taskId);

            // Return 202 Accepted with task ID
            Map<String, Object> response = new HashMap<>();
            response.put("taskId", taskId);
            response.put("status", "PENDING");
            response.put("message", "Merge processing initiated");
            return ResponseEntity.accepted().body(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error initiating merge", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to initiate merge: " + e.getMessage());
        }
    }

    /**
     * Retrieves the status of an ongoing or completed conversion task.
     * Returns the current status, progress, and error information if applicable.
     */
    @GetMapping("/status/{taskId}")
    public ResponseEntity<?> getTaskStatus(@PathVariable String taskId) {
        try {
            if (!taskRegistryService.taskExists(taskId)) {
                return ResponseEntity.notFound().build();
            }

            TaskStatus task = taskRegistryService.getTask(taskId);
            Map<String, Object> response = new HashMap<>();
            response.put("taskId", taskId);
            response.put("status", task.getStatus());
            response.put("fileName", task.getFileName());
            response.put("contentType", task.getContentType());
            response.put("errorMessage", task.getErrorMessage());
            response.put("createdAt", task.getCreatedAt());
            response.put("updatedAt", task.getUpdatedAt());
            response.put("resultSize", task.getResultContent() != null ? task.getResultContent().length : 0);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error retrieving task status: {}", taskId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to retrieve task status: " + e.getMessage());
        }
    }

    /**
     * Downloads the converted file if the task is completed.
     * Returns the file with appropriate headers for download.
     * Returns 404 if task not found, 202 if still processing, 400 if failed.
     */
    @GetMapping("/download/{taskId}")
    public ResponseEntity<?> downloadConvertedFile(@PathVariable String taskId) {
        try {
            if (!taskRegistryService.taskExists(taskId)) {
                return ResponseEntity.notFound().build();
            }

            TaskStatus task = taskRegistryService.getTask(taskId);

            // Check task status
            if ("PENDING".equals(task.getStatus()) || "PROCESSING".equals(task.getStatus())) {
                // Still processing
                Map<String, Object> response = new HashMap<>();
                response.put("taskId", taskId);
                response.put("status", task.getStatus());
                response.put("message", "Conversion is still processing. Please try again shortly.");
                return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
            }

            if ("FAILED".equals(task.getStatus())) {
                // Conversion failed
                Map<String, Object> response = new HashMap<>();
                response.put("taskId", taskId);
                response.put("status", "FAILED");
                response.put("errorMessage", task.getErrorMessage());
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            if ("COMPLETED".equals(task.getStatus())) {
                // Return the file
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType(task.getContentType()));
                headers.setContentDispositionFormData("attachment", task.getFileName());
                logger.info("Downloading completed task: {}, File: {}", taskId, task.getFileName());
                return new ResponseEntity<>(task.getResultContent(), headers, HttpStatus.OK);
            }

            // Unknown status
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unknown task status: " + task.getStatus());

        } catch (Exception e) {
            logger.error("Error downloading file: {}", taskId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to download file: " + e.getMessage());
        }
    }

    /**
     * Returns metrics about active and completed tasks (for monitoring).
     */
    @GetMapping("/metrics")
    public ResponseEntity<?> getMetrics() {
        try {
            return ResponseEntity.ok(taskRegistryService.getMetrics());
        } catch (Exception e) {
            logger.error("Error retrieving metrics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to retrieve metrics: " + e.getMessage());
        }
    }
}

