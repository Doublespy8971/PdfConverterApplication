package com.pm.pdfconverterapplication.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service to track and manage asynchronous conversion tasks.
 * Uses an in-memory registry of tasks with their current status and results.
 */
@Service
public class TaskRegistryService {

    private static final Logger logger = LoggerFactory.getLogger(TaskRegistryService.class);
    private final Map<String, TaskStatus> taskRegistry = new ConcurrentHashMap<>();
    private final long completedRetentionMillis;
    private final long processingTimeoutMillis;
    private final int maxTasks;
    private final long maxStoredResultBytes;
    private final long maxResultBytes;
    private final Clock clock;
    private final AtomicLong storedResultBytes = new AtomicLong();

    @Autowired
    public TaskRegistryService(
            @Value("${app.tasks.completed-retention-hours:2}") long completedRetentionHours,
            @Value("${app.tasks.processing-timeout-hours:6}") long processingTimeoutHours,
            @Value("${app.tasks.max-tasks:1000}") int maxTasks,
            @Value("${app.tasks.max-stored-result-bytes:536870912}") long maxStoredResultBytes,
            @Value("${app.tasks.max-result-bytes:104857600}") long maxResultBytes,
            Clock clock
    ) {
        if (maxTasks < 1 || maxStoredResultBytes < 1 || maxResultBytes < 1) {
            throw new IllegalArgumentException("Task registry limits must be positive");
        }
        this.completedRetentionMillis = TimeUnit.HOURS.toMillis(completedRetentionHours);
        this.processingTimeoutMillis = TimeUnit.HOURS.toMillis(processingTimeoutHours);
        this.maxTasks = maxTasks;
        this.maxStoredResultBytes = maxStoredResultBytes;
        this.maxResultBytes = maxResultBytes;
        this.clock = clock;
    }

    public TaskRegistryService(long completedRetentionHours, long processingTimeoutHours) {
        this(completedRetentionHours, processingTimeoutHours, 1000, 536870912, 104857600, Clock.systemUTC());
    }

    /**
     * Represents the status of an asynchronous conversion task.
     */
    public static class TaskStatus {
        private String status; // PENDING, PROCESSING, COMPLETED, FAILED
        private byte[] resultContent;
        private String fileName;
        private String contentType;
        private String errorMessage;
        private long createdAt;
        private long updatedAt;

        public TaskStatus(long now) {
            this.status = "PENDING";
            this.createdAt = now;
            this.updatedAt = now;
        }

        // Getters and setters
        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            setStatus(status, System.currentTimeMillis());
        }

        private void setStatus(String status, long now) {
            this.status = status;
            this.updatedAt = now;
        }

        public byte[] getResultContent() {
            return resultContent;
        }

        public void setResultContent(byte[] resultContent) {
            this.resultContent = resultContent;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public long getUpdatedAt() {
            return updatedAt;
        }

        @Override
        public String toString() {
            return "TaskStatus{" +
                    "status='" + status + '\'' +
                    ", fileName='" + fileName + '\'' +
                    ", contentType='" + contentType + '\'' +
                    ", errorMessage='" + errorMessage + '\'' +
                    ", resultSize=" + (resultContent != null ? resultContent.length : 0) +
                    '}';
        }
    }

    /**
     * Initiates a new task and returns its unique ID.
     *
     * @return UUID string for the task
     */
    public synchronized String initiateTask() {
        if (taskRegistry.size() >= maxTasks || storedResultBytes.get() >= maxStoredResultBytes) {
            throw new TaskCapacityExceededException();
        }
        String taskId = UUID.randomUUID().toString();
        TaskStatus taskStatus = new TaskStatus(clock.millis());
        taskRegistry.put(taskId, taskStatus);
        logger.info("Task initiated: {}", taskId);
        return taskId;
    }

    /**
     * Updates a task's status to PROCESSING.
     *
     * @param taskId The task ID
     */
    public void updateTaskProgress(String taskId) {
        TaskStatus taskStatus = taskRegistry.get(taskId);
        if (taskStatus != null) {
            taskStatus.setStatus("PROCESSING");
            logger.debug("Task {} status updated to PROCESSING", taskId);
        }
    }

    /**
     * Marks a task as completed with the conversion result.
     *
     * @param taskId      The task ID
     * @param content     The conversion result bytes
     * @param fileName    The output file name
     * @param contentType The MIME type
     */
    public synchronized void completeTask(String taskId, byte[] content, String fileName, String contentType) {
        TaskStatus taskStatus = taskRegistry.get(taskId);
        if (taskStatus != null) {
            if (content == null || content.length > maxResultBytes
                    || storedResultBytes.get() + content.length > maxStoredResultBytes) {
                taskStatus.setErrorMessage("Conversion result cannot be stored at this time.");
                taskStatus.setStatus("FAILED", clock.millis());
                return;
            }
            taskStatus.setResultContent(content);
            storedResultBytes.addAndGet(content.length);
            taskStatus.setFileName(fileName);
            taskStatus.setContentType(contentType);
            taskStatus.setStatus("COMPLETED", clock.millis());
            logger.info("Task {} completed successfully: {}", taskId, fileName);
        }
    }

    /**
     * Marks a task as failed with an error message.
     *
     * @param taskId       The task ID
     * @param errorMessage The error description
     */
    public void failTask(String taskId, String errorMessage) {
        TaskStatus taskStatus = taskRegistry.get(taskId);
        if (taskStatus != null) {
            taskStatus.setErrorMessage(errorMessage);
            taskStatus.setStatus("FAILED");
            logger.error("Task {} failed: {}", taskId, errorMessage);
        }
    }

    /**
     * Retrieves the status of a task.
     *
     * @param taskId The task ID
     * @return TaskStatus or null if not found
     */
    public TaskStatus getTask(String taskId) {
        return taskRegistry.get(taskId);
    }

    /**
     * Checks if a task exists.
     *
     * @param taskId The task ID
     * @return true if task exists, false otherwise
     */
    public boolean taskExists(String taskId) {
        return taskRegistry.containsKey(taskId);
    }

    /**
     * Removes a completed or failed task from the registry (cleanup).
     * Should be called after download or after TTL expires.
     *
     * @param taskId The task ID
     */
    public void removeTask(String taskId) {
        TaskStatus removed = taskRegistry.remove(taskId);
        if (removed != null && removed.getResultContent() != null) {
            storedResultBytes.addAndGet(-removed.getResultContent().length);
        }
        logger.debug("Task {} removed from registry", taskId);
    }

    /**
     * Returns task metrics/statistics.
     *
     * @return Map of metrics
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalTasks", taskRegistry.size());
        metrics.put("pendingTasks", taskRegistry.values().stream().filter(t -> "PENDING".equals(t.getStatus())).count());
        metrics.put("processingTasks", taskRegistry.values().stream().filter(t -> "PROCESSING".equals(t.getStatus())).count());
        metrics.put("completedTasks", taskRegistry.values().stream().filter(t -> "COMPLETED".equals(t.getStatus())).count());
        metrics.put("failedTasks", taskRegistry.values().stream().filter(t -> "FAILED".equals(t.getStatus())).count());
        return metrics;
    }

    @Scheduled(fixedRateString = "${app.tasks.cleanup-interval-ms:3600000}")
    public void cleanupExpiredTasks() {
        cleanupExpiredTasks(System.currentTimeMillis());
    }

    void cleanupExpiredTasks(long nowMillis) {
        taskRegistry.entrySet().removeIf(entry -> {
            if (!shouldExpire(entry.getValue(), nowMillis)) {
                return false;
            }
            TaskStatus removed = taskRegistry.remove(entry.getKey());
            if (removed != null && removed.getResultContent() != null) {
                storedResultBytes.addAndGet(-removed.getResultContent().length);
            }
            return true;
        });
    }

    private boolean shouldExpire(TaskStatus taskStatus, long nowMillis) {
        String status = taskStatus.getStatus();
        if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
            return nowMillis - taskStatus.getUpdatedAt() > completedRetentionMillis;
        }
        if ("PENDING".equals(status) || "PROCESSING".equals(status)) {
            return nowMillis - taskStatus.getCreatedAt() > processingTimeoutMillis;
        }
        return false;
    }

    public long getStoredResultBytes() {
        return storedResultBytes.get();
    }

    public int getTaskCount() {
        return taskRegistry.size();
    }

    public static class TaskCapacityExceededException extends RuntimeException {
        public TaskCapacityExceededException() {
            super("Task registry is temporarily at capacity.");
        }
    }
}
