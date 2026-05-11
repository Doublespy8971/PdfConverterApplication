package com.pm.pdfconverterapplication.service;

import com.google.gson.Gson;
import com.pm.pdfconverterapplication.model.SummaryResult;
import com.pm.pdfconverterapplication.provider.LLMProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

@Service
public class AsyncSummarizationWorker {

    private static final Logger logger = LoggerFactory.getLogger(AsyncSummarizationWorker.class);

    private final LLMProvider llmProvider;
    private final TaskRegistryService taskRegistryService;
    private final Gson gson = new Gson();

    public AsyncSummarizationWorker(LLMProvider llmProvider, TaskRegistryService taskRegistryService) {
        this.llmProvider = llmProvider;
        this.taskRegistryService = taskRegistryService;
    }

    @Async
    public void summarizeAsync(String filePath, String originalFilename, String summaryLength, String taskId) {
        try {
            logger.info("Async summarization started - Task: {}, File: {}", taskId, originalFilename);
            taskRegistryService.updateTaskProgress(taskId);

            MultipartFile file = createMultipartFileFromPath(filePath, originalFilename);
            SummaryResult result = llmProvider.summarizePdf(file, summaryLength);

            String payload = gson.toJson(result);
            taskRegistryService.completeTask(taskId, payload.getBytes(StandardCharsets.UTF_8), "summary.json", "application/json");
            logger.info("Async summarization completed - Task: {}", taskId);

        } catch (Exception e) {
            logger.error("Async summarization failed - Task: {}, Error: {}", taskId, e.getMessage(), e);
            taskRegistryService.failTask(taskId, "Summarization failed: " + e.getMessage());
        } finally {
            cleanupTemporaryDirectory(filePath);
        }
    }

    private MultipartFile createMultipartFileFromPath(String filePath, String filename) throws IOException {
        return new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return filename;
            }

            @Override
            public String getContentType() {
                return "application/pdf";
            }

            @Override
            public boolean isEmpty() {
                try {
                    return Files.size(Path.of(filePath)) == 0;
                } catch (IOException e) {
                    return true;
                }
            }

            @Override
            public long getSize() {
                try {
                    return Files.size(Path.of(filePath));
                } catch (IOException e) {
                    return 0;
                }
            }

            @Override
            public byte[] getBytes() throws IOException {
                return Files.readAllBytes(Path.of(filePath));
            }

            @Override
            public java.io.InputStream getInputStream() throws IOException {
                return Files.newInputStream(Path.of(filePath));
            }

            @Override
            public void transferTo(File dest) throws IOException, IllegalStateException {
                Files.copy(Path.of(filePath), dest.toPath());
            }

            @Override
            public void transferTo(Path dest) throws IOException, IllegalStateException {
                Files.copy(Path.of(filePath), dest);
            }
        };
    }

    private void cleanupTemporaryDirectory(String filePath) {
        try {
            Path dirPath = Path.of(filePath).getParent();
            if (dirPath != null && dirPath.getFileName().toString().startsWith("ai_")) {
                Files.walk(dirPath)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                logger.warn("Failed to delete: {}", path, e);
                            }
                        });
            }
        } catch (IOException e) {
            logger.warn("Failed to cleanup temporary directory", e);
        }
    }
}
