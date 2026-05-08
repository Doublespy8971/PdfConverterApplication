package com.pm.pdfconverterapplication.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
      * @param filePath     Path to the uploaded file
      * @param originalFilename The original file name
      * @param tool          The conversion tool to use
      * @param taskId        The task ID for tracking progress
      */
     @Async
     public void convertFileAsync(String filePath, String originalFilename, String tool, String taskId) {
         try {
             logger.info("Async conversion started - Task: {}, Tool: {}, File: {}", taskId, tool, originalFilename);
             taskRegistryService.updateTaskProgress(taskId);

             // Create a MultipartFile wrapper from the file
             MultipartFile file = createMultipartFileFromPath(filePath, originalFilename);

             // Perform the conversion using the synchronous service
             ConversionService.ConversionResult result = conversionService.convert(file, tool);

             // Complete the task with the result
             taskRegistryService.completeTask(taskId, result.content(), result.fileName(), result.contentType());
             logger.info("Async conversion completed - Task: {}, File: {}", taskId, result.fileName());

         } catch (Exception e) {
             logger.error("Async conversion failed - Task: {}, Error: {}", taskId, e.getMessage(), e);
             taskRegistryService.failTask(taskId, "Conversion failed: " + e.getMessage());
         } finally {
             // Clean up temporary file
             cleanupTemporaryFile(filePath);
         }
     }

     /**
      * Asynchronously converts multiple files in batch mode.
      * Executes in a separate thread pool to prevent HTTP timeouts.
      *
      * @param filePaths    Array of file paths
      * @param filenames     Array of original file names
      * @param tool          The conversion tool to use
      * @param taskId        The task ID for tracking progress
      */
     @Async
     public void batchConvertAsync(String[] filePaths, String[] filenames, String tool, String taskId) {
         try {
             logger.info("Async batch conversion started - Task: {}, Tool: {}, Files: {}", taskId, tool, filenames.length);
             taskRegistryService.updateTaskProgress(taskId);

             // Create MultipartFile wrappers from the file paths
             MultipartFile[] files = new MultipartFile[filePaths.length];
             for (int i = 0; i < filePaths.length; i++) {
                 files[i] = createMultipartFileFromPath(filePaths[i], filenames[i]);
             }

             // Perform the batch conversion
             ConversionService.ConversionResult result = conversionService.batchConvert(files, tool);

             // Complete the task with the result
             taskRegistryService.completeTask(taskId, result.content(), result.fileName(), result.contentType());
             logger.info("Async batch conversion completed - Task: {}, Output: {}", taskId, result.fileName());

         } catch (Exception e) {
             logger.error("Async batch conversion failed - Task: {}, Error: {}", taskId, e.getMessage(), e);
             taskRegistryService.failTask(taskId, "Batch conversion failed: " + e.getMessage());
         } finally {
             // Clean up temporary files
             cleanupTemporaryDirectory(filePaths[0]);
         }
     }

     /**
      * Asynchronously merges multiple PDF files.
      *
      * @param filePaths  Array of file paths
      * @param filenames  Array of original file names
      * @param taskId     The task ID for tracking progress
      */
     @Async
     public void mergePdfsAsync(String[] filePaths, String[] filenames, String taskId) {
         try {
             logger.info("Async PDF merge started - Task: {}, Files: {}", taskId, filenames.length);
             taskRegistryService.updateTaskProgress(taskId);

             // Create MultipartFile wrappers from the file paths
             MultipartFile[] files = new MultipartFile[filePaths.length];
             for (int i = 0; i < filePaths.length; i++) {
                 files[i] = createMultipartFileFromPath(filePaths[i], filenames[i]);
             }

             // Perform the merge
             ConversionService.ConversionResult result = conversionService.mergePdfFiles(files);

             // Complete the task with the result
             taskRegistryService.completeTask(taskId, result.content(), result.fileName(), result.contentType());
             logger.info("Async PDF merge completed - Task: {}, Output: {}", taskId, result.fileName());

         } catch (Exception e) {
             logger.error("Async PDF merge failed - Task: {}, Error: {}", taskId, e.getMessage(), e);
             taskRegistryService.failTask(taskId, "PDF merge failed: " + e.getMessage());
         } finally {
             // Clean up temporary files
             cleanupTemporaryDirectory(filePaths[0]);
         }
     }

     /**
      * Helper method to create a MultipartFile wrapper from a file path.
      * This allows us to pass the file through the ConversionService with streaming.
      *
      * @param filePath The file path
      * @param filename The filename
      * @return MultipartFile wrapper that reads from disk via streaming
      */
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
                 return "application/octet-stream";
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

     /**
      * Helper method to delete a temporary file.
      *
      * @param filePath Path to the file to delete
      */
     private void cleanupTemporaryFile(String filePath) {
         try {
             Files.deleteIfExists(Path.of(filePath));
         } catch (IOException e) {
             logger.warn("Failed to delete temporary file: {}", filePath, e);
         }
     }

     /**
      * Helper method to delete a temporary directory and all its contents.
      *
      * @param filePath Path to any file within the directory
      */
     private void cleanupTemporaryDirectory(String filePath) {
         try {
             Path dirPath = Path.of(filePath).getParent();
             if (dirPath != null && dirPath.toString().contains("_")) {
                 // Delete directory and all contents
                 Files.walk(dirPath)
                      .sorted((a, b) -> b.compareTo(a))  // Sort descending to delete files before dirs
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

