package com.pm.pdfconverterapplication.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskRegistryServiceTest {

    @Test
    void cleanupExpiredTasksRemovesCompletedTasks() {
        TaskRegistryService service = new TaskRegistryService(0, 0);
        String taskId = service.initiateTask();
        TaskRegistryService.TaskStatus taskStatus = service.getTask(taskId);
        taskStatus.setStatus("COMPLETED");

        service.cleanupExpiredTasks(System.currentTimeMillis() + 1);

        assertFalse(service.taskExists(taskId));
    }

    @Test
    void cleanupExpiredTasksKeepsRecentPendingTasks() {
        TaskRegistryService service = new TaskRegistryService(1, 1);
        String taskId = service.initiateTask();

        service.cleanupExpiredTasks(System.currentTimeMillis());

        assertTrue(service.taskExists(taskId));
    }
}
