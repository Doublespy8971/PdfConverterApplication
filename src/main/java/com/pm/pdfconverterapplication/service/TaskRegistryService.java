package com.pm.pdfconverterapplication.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to track and manage asynchronous conversion tasks.
 * Uses an in-memory registry of tasks with their current status and results.
 */
@Service
public class TaskRegistryService {

    private static final Logger logger = LoggerFactory.getLogger(TaskRegistryService.class);
    private final Map<String, TaskStatus> taskRegistry = new ConcurrentHashMap<>();

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

        public TaskStatus() {
            this.status = "PENDING";
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = System.currentTimeMillis();
        }

        // Getters and setters
        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
            this.updatedAt = System.currentTimeMillis();
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
    public String initiateTask() {
        String taskId = UUID.randomUUID().toString();
        TaskStatus taskStatus = new TaskStatus();
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
    public void completeTask(String taskId, byte[] content, String fileName, String contentType) {
        TaskStatus taskStatus = taskRegistry.get(taskId);
        if (taskStatus != null) {
            taskStatus.setResultContent(content);
            taskStatus.setFileName(fileName);
            taskStatus.setContentType(contentType);
            taskStatus.setStatus("COMPLETED");
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
        taskRegistry.remove(taskId);
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
}

