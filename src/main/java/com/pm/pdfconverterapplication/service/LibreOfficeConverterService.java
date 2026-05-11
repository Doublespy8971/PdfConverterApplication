package com.pm.pdfconverterapplication.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.pm.pdfconverterapplication.util.FileNameUtils;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;

@Service
public class LibreOfficeConverterService {

    private static final String LIBREOFFICE_CMD = "soffice";
    private static final long CONVERSION_TIMEOUT = 120; // 2 minutes
    private final Semaphore semaphore = new Semaphore(2);  // Limit to 2 concurrent LibreOffice processes

    public byte[] convertOfficeDocumentToPdf(MultipartFile file) throws Exception {
        if (!isLibreOfficeAvailable()) {
            throw new IllegalStateException(
                "LibreOffice is not available. Ensure LibreOffice is installed on your system."
            );
        }

        // Attempt to acquire a permit with 30 second timeout
        if (!semaphore.tryAcquire(30, TimeUnit.SECONDS)) {
            throw new RuntimeException("Server is currently processing at maximum capacity. Please try again in a minute.");
        }

        try {
            // Create temporary directory in /tmp (works in Docker)
            Path tempDirPath = Files.createTempDirectory("lo_convert_");
            File tempDir = tempDirPath.toFile();
            tempDir.deleteOnExit();

            // Create input file with proper extension
            String extension = getFileExtension(file.getOriginalFilename());
            File inputFile = new File(tempDir, "input" + extension);
            file.transferTo(inputFile);
            inputFile.deleteOnExit();

            File outputFile = new File(tempDir, "input.pdf");

            try {
                // Build LibreOffice command with proper error handling
                ProcessBuilder pb = new ProcessBuilder(
                    LIBREOFFICE_CMD,
                    "--headless",
                    "--safe-mode",
                    "--convert-to", "pdf",
                    "--outdir", tempDir.getAbsolutePath(),
                    inputFile.getAbsolutePath()
                );

                // Set environment for LibreOffice
                pb.environment().put("HOME", "/tmp");
                pb.redirectErrorStream(true);

                Process process = pb.start();

                // Capture output for debugging
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }

                // Wait for process to complete with timeout
                boolean completed = process.waitFor(CONVERSION_TIMEOUT, TimeUnit.SECONDS);

                if (!completed) {
                    process.destroy();
                    throw new RuntimeException("LibreOffice conversion timeout after " + CONVERSION_TIMEOUT + " seconds");
                }

                int exitCode = process.exitValue();

                // Check for output file with multiple attempts (Docker timing issue)
                int maxAttempts = 10;
                int attempts = 0;
                while (!outputFile.exists() && attempts < maxAttempts) {
                    Thread.sleep(500); // Wait 500ms
                    attempts++;
                }

                if (!outputFile.exists()) {
                    String errorMsg = "LibreOffice failed to create output PDF file.\n";
                    errorMsg += "Exit Code: " + exitCode + "\n";
                    errorMsg += "Output: " + output.toString() + "\n";
                    errorMsg += "Input file: " + inputFile.getAbsolutePath() + "\n";
                    errorMsg += "Output file: " + outputFile.getAbsolutePath() + "\n";
                    throw new RuntimeException(errorMsg);
                }

                // Read and return PDF bytes
                byte[] pdfBytes = Files.readAllBytes(outputFile.toPath());

                if (pdfBytes.length == 0) {
                    throw new RuntimeException("LibreOffice created an empty PDF file");
                }

                return pdfBytes;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("LibreOffice conversion was interrupted: " + e.getMessage(), e);
            } finally {
                // Clean up temporary files
                try {
                    if (inputFile.exists()) {
                        inputFile.delete();
                    }
                    if (outputFile.exists()) {
                        outputFile.delete();
                    }
                    if (tempDir.exists()) {
                        String[] files = tempDir.list();
                        if (files != null) {
                            for (String f : files) {
                                new File(tempDir, f).delete();
                            }
                        }
                        tempDir.delete();
                    }
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        } finally {
            // Always release the semaphore permit
            semaphore.release();
        }
    }

    private String getFileExtension(String fileName) {
        String extension = FileNameUtils.getSafeExtension(fileName);
        return extension.isBlank() ? "" : "." + extension;
    }

    public boolean isLibreOfficeAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(LIBREOFFICE_CMD, "--version");
            pb.redirectErrorStream(true);
            pb.environment().put("HOME", "/tmp");

            Process process = pb.start();
            boolean completed = process.waitFor(10, TimeUnit.SECONDS);

            if (!completed) {
                process.destroy();
                return false;
            }

            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
