package com.pm.pdfconverterapplication.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void cleanupExpiresCompletedAndStuckTasksUsingInjectedClock() {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC);
        TaskRegistryService service = new TaskRegistryService(1, 1, 10, 100, 100, clock);
        String completedId = service.initiateTask();
        service.completeTask(completedId, new byte[]{1}, "result.pdf", "application/pdf");
        String stuckId = service.initiateTask();

        service.cleanupExpiredTasks(1_000 + 3_600_001);

        assertFalse(service.taskExists(completedId));
        assertFalse(service.taskExists(stuckId));
    }

    @Test
    void rejectsNewTasksWhenTaskCountOrStoredBytesCapIsReached() {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC);
        TaskRegistryService countLimited = new TaskRegistryService(1, 1, 1, 100, 100, clock);
        countLimited.initiateTask();
        assertThrows(TaskRegistryService.TaskCapacityExceededException.class, countLimited::initiateTask);

        TaskRegistryService bytesLimited = new TaskRegistryService(1, 1, 10, 1, 100, clock);
        String taskId = bytesLimited.initiateTask();
        bytesLimited.completeTask(taskId, new byte[]{1}, "result.pdf", "application/pdf");
        assertThrows(TaskRegistryService.TaskCapacityExceededException.class, bytesLimited::initiateTask);
    }
}
