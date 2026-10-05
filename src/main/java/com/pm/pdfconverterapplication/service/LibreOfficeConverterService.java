package com.pm.pdfconverterapplication.service;

import com.pm.pdfconverterapplication.util.FileNameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class LibreOfficeConverterService {

    private static final Logger logger = LoggerFactory.getLogger(LibreOfficeConverterService.class);

    private final String executable;
    private final long conversionTimeoutSeconds;
    private final int permitCount;
    private final Semaphore semaphore;
    private volatile Boolean libreOfficeAvailable;

    public LibreOfficeConverterService(
            @Value("${app.libreoffice.command:soffice}") String executable,
            @Value("${app.libreoffice.permits:2}") int permitCount,
            @Value("${app.libreoffice.timeout-seconds:120}") long conversionTimeoutSeconds) {
        if (permitCount < 1) {
            throw new IllegalArgumentException("LibreOffice permit count must be at least 1");
        }
        if (conversionTimeoutSeconds < 1) {
            throw new IllegalArgumentException("LibreOffice timeout must be at least 1 second");
        }
        this.executable = executable;
        this.conversionTimeoutSeconds = conversionTimeoutSeconds;
        this.permitCount = permitCount;
        this.semaphore = new Semaphore(permitCount);
    }

    public byte[] convertOfficeDocumentToPdf(MultipartFile file) throws Exception {
        if (!isLibreOfficeAvailable()) {
            throw new IllegalStateException("LibreOffice is not available.");
        }
        if (!semaphore.tryAcquire(30, TimeUnit.SECONDS)) {
            throw new RuntimeException("Server is currently processing at maximum capacity. Please try again in a minute.");
        }

        Path tempDir = null;
        Path profileDir = null;
        try {
            tempDir = Files.createTempDirectory("lo_convert_");
            profileDir = Files.createTempDirectory("lo_profile_" + UUID.randomUUID() + "_");
            String extension = getFileExtension(file.getOriginalFilename());
            Path inputFile = tempDir.resolve("input" + extension);
            Path outputFile = tempDir.resolve("input.pdf");
            Path logFile = tempDir.resolve("soffice.log");
            file.transferTo(inputFile);

            ProcessBuilder processBuilder = new ProcessBuilder(
                    executable,
                    "--headless",
                    "--safe-mode",
                    "-env:UserInstallation=" + profileDir.toUri(),
                    "--convert-to", "pdf",
                    "--outdir", tempDir.toAbsolutePath().toString(),
                    inputFile.toAbsolutePath().toString());
            processBuilder.environment().put("HOME", "/tmp");
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(logFile.toFile());

            Process process = processBuilder.start();
            boolean completed = process.waitFor(conversionTimeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                destroyProcessTree(process);
                logger.error("LibreOffice conversion timed out after {} seconds", conversionTimeoutSeconds);
                throw new RuntimeException("LibreOffice conversion timed out.");
            }

            int exitCode = process.exitValue();
            String processOutput = readLog(logFile);
            if (exitCode != 0) {
                logger.error("LibreOffice exited with code {}. Output: {}", exitCode, processOutput);
                throw new RuntimeException("LibreOffice conversion failed.");
            }
            if (!Files.isRegularFile(outputFile)) {
                logger.error("LibreOffice did not create an output file. Exit code: {}. Output: {}",
                        exitCode, processOutput);
                throw new RuntimeException("LibreOffice conversion did not produce a PDF.");
            }

            byte[] pdfBytes = Files.readAllBytes(outputFile);
            if (pdfBytes.length == 0) {
                logger.error("LibreOffice created an empty PDF. Output: {}", processOutput);
                throw new RuntimeException("LibreOffice conversion produced an empty PDF.");
            }
            return pdfBytes;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("LibreOffice conversion was interrupted.", e);
        } catch (IOException e) {
            logger.error("LibreOffice conversion failed", e);
            throw new RuntimeException("LibreOffice conversion failed.", e);
        } finally {
            deleteRecursively(tempDir);
            deleteRecursively(profileDir);
            semaphore.release();
        }
    }

    private String readLog(Path logFile) {
        if (logFile == null || !Files.isRegularFile(logFile)) {
            return "";
        }
        try {
            String output = Files.readString(logFile);
            return output.length() > 4000 ? output.substring(0, 4000) : output;
        } catch (IOException e) {
            logger.warn("Could not read LibreOffice process log", e);
            return "";
        }
    }

    private void destroyProcessTree(Process process) {
        List<ProcessHandle> descendants = process.descendants().toList();
        descendants.forEach(handle -> handle.destroyForcibly());
        process.destroyForcibly();
        descendants.forEach(handle -> {
            try {
                handle.onExit().get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.warn("LibreOffice child process did not exit promptly");
            }
        });
    }

    private void deleteRecursively(Path directory) {
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    logger.warn("Could not delete LibreOffice temporary path {}", path, e);
                }
            });
        } catch (IOException e) {
            logger.warn("Could not clean LibreOffice temporary directory", e);
        }
    }

    private String getFileExtension(String fileName) {
        String extension = FileNameUtils.getSafeExtension(fileName);
        return extension.isBlank() ? "" : "." + extension;
    }

    public boolean isLibreOfficeAvailable() {
        Boolean cached = libreOfficeAvailable;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (libreOfficeAvailable == null) {
                libreOfficeAvailable = checkLibreOfficeAvailable();
            }
            return libreOfficeAvailable;
        }
    }

    public int getPermitsInUse() {
        return permitCount - semaphore.availablePermits();
    }

    private boolean checkLibreOfficeAvailable() {
        try {
            Process process = new ProcessBuilder(executable, "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean completed = process.waitFor(10, TimeUnit.SECONDS);
            if (!completed) {
                destroyProcessTree(process);
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            logger.warn("LibreOffice availability check failed", e);
            return false;
        }
    }
}
