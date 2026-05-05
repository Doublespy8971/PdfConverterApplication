package com.pm.pdfconverterapplication.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class TempFileCleanupTask {

    private static final Logger logger = LoggerFactory.getLogger(TempFileCleanupTask.class);
    private static final String TEMP_DIR_PREFIX = "lo_convert_";
    private static final long CLEANUP_AGE_HOURS = 2;

    /**
     * Runs every hour (3600000 milliseconds) to clean up abandoned temporary files
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanupOldTempFiles() {
        try {
            String tempDirPath = System.getProperty("java.io.tmpdir");
            Path tempDir = Paths.get(tempDirPath);

            if (!Files.exists(tempDir)) {
                logger.warn("Temp directory does not exist: {}", tempDir);
                return;
            }

            int cleanedCount = 0;

            // Walk through the temp directory
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(tempDir)) {
                for (Path path : directoryStream) {
                    // Check if it's a directory and starts with lo_convert_ prefix
                    if (Files.isDirectory(path) && path.getFileName().toString().startsWith(TEMP_DIR_PREFIX)) {
                        // Check if directory is older than 2 hours
                        if (isOlderThan2Hours(path)) {
                            try {
                                deleteDirectoryRecursively(path);
                                cleanedCount++;
                                logger.debug("Deleted abandoned temp directory: {}", path);
                            } catch (IOException e) {
                                logger.warn("Failed to delete temp directory: {} - Error: {}", path, e.getMessage());
                            }
                        }
                    }
                }
            }

            if (cleanedCount > 0) {
                logger.info("Cleanup completed: {} abandoned LibreOffice temp directories removed", cleanedCount);
            } else {
                logger.debug("Cleanup completed: No abandoned directories found");
            }

        } catch (IOException e) {
            logger.error("Error during temp file cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Checks if a directory's last modified time is older than 2 hours
     */
    private boolean isOlderThan2Hours(Path dirPath) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(dirPath, BasicFileAttributes.class);
            Instant lastModifiedTime = attributes.lastModifiedTime().toInstant();
            Instant twoHoursAgo = Instant.now().minus(CLEANUP_AGE_HOURS, ChronoUnit.HOURS);

            return lastModifiedTime.isBefore(twoHoursAgo);
        } catch (IOException e) {
            logger.warn("Failed to read file attributes for: {} - Error: {}", dirPath, e.getMessage());
            return false;
        }
    }

    /**
     * Recursively deletes a directory and all its contents
     */
    private void deleteDirectoryRecursively(Path dirPath) throws IOException {
        if (!Files.exists(dirPath)) {
            return;
        }

        // Walk through all files in the directory tree
        try (var stream = Files.walk(dirPath)) {
            stream
                    // Sort by path length descending so we delete files before directories
                    .sorted((p1, p2) -> p2.toString().length() - p1.toString().length())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            logger.warn("Failed to delete: {} - Error: {}", path, e.getMessage());
                        }
                    });
        }

        // Final attempt to delete the directory itself
        if (Files.exists(dirPath)) {
            Files.delete(dirPath);
        }
    }
}


