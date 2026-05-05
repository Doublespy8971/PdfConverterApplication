package com.pm.pdfconverterapplication.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Asynchronous worker service that processes file conversions in background threads.
 * Uses Spring's @Async annotation to prevent HTTP request timeouts on long-running conversions.
 */
@Service
public class AsyncConversionWorker {

    private static final Logger logger = LoggerFactory.getLogger(AsyncConversionWorker.class);

    @Autowired
    private ConversionService conversionService;

    @Autowired
    private TaskRegistryService taskRegistryService;

    /**
     * Asynchronously converts a single file.
     * Executes in a separate thread pool to prevent HTTP timeouts.
     *
     * @param fileBytes     The file content as byte array
     * @param originalFilename The original file name
     * @param tool          The conversion tool to use
     * @param taskId        The task ID for tracking progress
     */
    @Async
    public void convertFileAsync(byte[] fileBytes, String originalFilename, String tool, String taskId) {
        try {
            logger.info("Async conversion started - Task: {}, Tool: {}, File: {}", taskId, tool, originalFilename);
            taskRegistryService.updateTaskProgress(taskId);

            // Create a MultipartFile wrapper from the byte array
            MultipartFile file = createMultipartFileFromBytes(fileBytes, originalFilename);

            // Perform the conversion using the synchronous service
            ConversionService.ConversionResult result = conversionService.convert(file, tool);

            // Complete the task with the result
            taskRegistryService.completeTask(taskId, result.content(), result.fileName(), result.contentType());
            logger.info("Async conversion completed - Task: {}, File: {}", taskId, result.fileName());

        } catch (Exception e) {
            logger.error("Async conversion failed - Task: {}, Error: {}", taskId, e.getMessage(), e);
            taskRegistryService.failTask(taskId, "Conversion failed: " + e.getMessage());
        }
    }

    /**
     * Asynchronously converts multiple files in batch mode.
     * Executes in a separate thread pool to prevent HTTP timeouts.
     *
     * @param filesBytes    Array of file contents as byte arrays
     * @param filenames     Array of original file names
     * @param tool          The conversion tool to use
     * @param taskId        The task ID for tracking progress
     */
    @Async
    public void batchConvertAsync(byte[][] filesBytes, String[] filenames, String tool, String taskId) {
        try {
            logger.info("Async batch conversion started - Task: {}, Tool: {}, Files: {}", taskId, tool, filenames.length);
            taskRegistryService.updateTaskProgress(taskId);

            // Create MultipartFile wrappers from the byte arrays
            MultipartFile[] files = new MultipartFile[filesBytes.length];
            for (int i = 0; i < filesBytes.length; i++) {
                files[i] = createMultipartFileFromBytes(filesBytes[i], filenames[i]);
            }

            // Perform the batch conversion
            ConversionService.ConversionResult result = conversionService.batchConvert(files, tool);

            // Complete the task with the result
            taskRegistryService.completeTask(taskId, result.content(), result.fileName(), result.contentType());
            logger.info("Async batch conversion completed - Task: {}, Output: {}", taskId, result.fileName());

        } catch (Exception e) {
            logger.error("Async batch conversion failed - Task: {}, Error: {}", taskId, e.getMessage(), e);
            taskRegistryService.failTask(taskId, "Batch conversion failed: " + e.getMessage());
        }
    }

    /**
     * Asynchronously merges multiple PDF files.
     *
     * @param filesBytes Array of file contents as byte arrays
     * @param filenames  Array of original file names
     * @param taskId     The task ID for tracking progress
     */
    @Async
    public void mergePdfsAsync(byte[][] filesBytes, String[] filenames, String taskId) {
        try {
            logger.info("Async PDF merge started - Task: {}, Files: {}", taskId, filenames.length);
            taskRegistryService.updateTaskProgress(taskId);

            // Create MultipartFile wrappers from the byte arrays
            MultipartFile[] files = new MultipartFile[filesBytes.length];
            for (int i = 0; i < filesBytes.length; i++) {
                files[i] = createMultipartFileFromBytes(filesBytes[i], filenames[i]);
            }

            // Perform the merge
            ConversionService.ConversionResult result = conversionService.mergePdfFiles(files);

            // Complete the task with the result
            taskRegistryService.completeTask(taskId, result.content(), result.fileName(), result.contentType());
            logger.info("Async PDF merge completed - Task: {}, Output: {}", taskId, result.fileName());

        } catch (Exception e) {
            logger.error("Async PDF merge failed - Task: {}, Error: {}", taskId, e.getMessage(), e);
            taskRegistryService.failTask(taskId, "PDF merge failed: " + e.getMessage());
        }
    }

    /**
     * Helper method to create a MultipartFile wrapper from byte array.
     * This allows us to pass the bytes through the ConversionService.
     *
     * @param fileBytes The file content
     * @param filename  The filename
     * @return MultipartFile wrapper
     */
    private MultipartFile createMultipartFileFromBytes(byte[] fileBytes, String filename) {
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
                return "application/octet-stream";
            }

            @Override
            public boolean isEmpty() {
                return fileBytes.length == 0;
            }

            @Override
            public long getSize() {
                return fileBytes.length;
            }

            @Override
            public byte[] getBytes() throws IOException {
                return fileBytes;
            }

            @Override
            public java.io.InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(fileBytes);
            }

            @Override
            public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
                java.nio.file.Files.write(dest.toPath(), fileBytes);
            }

            @Override
            public void transferTo(java.nio.file.Path dest) throws IOException, IllegalStateException {
                java.nio.file.Files.write(dest, fileBytes);
            }
        };
    }
}

