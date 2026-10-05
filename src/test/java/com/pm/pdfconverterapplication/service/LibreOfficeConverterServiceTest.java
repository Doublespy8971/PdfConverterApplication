package com.pm.pdfconverterapplication.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.WINDOWS)
class LibreOfficeConverterServiceTest {

    private Path script;
    private Path workDir;

    @AfterEach
    void cleanup() throws IOException {
        if (workDir != null) {
            try (var paths = Files.walk(workDir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
    }

    @Test
    void convertsSuccessfully() throws Exception {
        createScript("""
                if [ "$1" = "--version" ]; then exit 0; fi
                cp "$7/input.docx" "$7/input.pdf"
                """);

        LibreOfficeConverterService service = new LibreOfficeConverterService(script.toString(), 1, 2);
        byte[] result = service.convertOfficeDocumentToPdf(file("input.docx", "pdf"));

        assertArrayEquals("pdf".getBytes(), result);
    }

    @Test
    void rejectsNonZeroExit() throws Exception {
        createScript("""
                if [ "$1" = "--version" ]; then exit 0; fi
                exit 3
                """);

        LibreOfficeConverterService service = new LibreOfficeConverterService(script.toString(), 1, 2);
        assertThrows(RuntimeException.class, () -> service.convertOfficeDocumentToPdf(file("input.docx", "input")));
    }

    @Test
    void rejectsEmptyOutput() throws Exception {
        createScript("""
                if [ "$1" = "--version" ]; then exit 0; fi
                touch "$7/input.pdf"
                """);

        LibreOfficeConverterService service = new LibreOfficeConverterService(script.toString(), 1, 2);
        assertThrows(RuntimeException.class, () -> service.convertOfficeDocumentToPdf(file("input.docx", "input")));
    }

    @Test
    void retriesAvailabilityAfterNegativeCacheInterval() throws Exception {
        createScript("""
                if [ "$1" = "--version" ]; then exit 1; fi
                """);
        LibreOfficeConverterService service = new LibreOfficeConverterService(script.toString(), 1, 2);

        assertTrue(!service.isLibreOfficeAvailable());
        Files.writeString(script, "#!/bin/sh\nif [ \"$1\" = \"--version\" ]; then exit 0; fi\n");
        assertTrue(!service.isLibreOfficeAvailable());

        Field lastCheck = LibreOfficeConverterService.class.getDeclaredField("lastAvailabilityCheckMillis");
        lastCheck.setAccessible(true);
        lastCheck.setLong(service, System.currentTimeMillis() - 61_000);
        assertTrue(service.isLibreOfficeAvailable());
    }

    @Test
    void interruptingConversionStopsTheProcess() throws Exception {
        createScript("""
                if [ "$1" = "--version" ]; then exit 0; fi
                sleep 30
                """);
        LibreOfficeConverterService service = new LibreOfficeConverterService(script.toString(), 1, 30);
        Thread conversion = new Thread(() -> assertThrows(RuntimeException.class,
                () -> service.convertOfficeDocumentToPdf(file("input.docx", "input"))));
        conversion.start();
        Thread.sleep(250);
        conversion.interrupt();
        conversion.join(5_000);
        assertTrue(!conversion.isAlive());
    }

    @Test
    void killsHungProcessWithinTimeout() throws Exception {
        createScript("""
                if [ "$1" = "--version" ]; then exit 0; fi
                sleep 30
                """);

        LibreOfficeConverterService service = new LibreOfficeConverterService(script.toString(), 1, 1);
        long start = System.nanoTime();
        assertThrows(RuntimeException.class, () -> service.convertOfficeDocumentToPdf(file("input.docx", "input")));
        assertTrue((System.nanoTime() - start) < 10_000_000_000L);
    }

    private MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content.getBytes());
    }

    private void createScript(String body) throws IOException {
        workDir = Files.createTempDirectory("libreoffice-test-");
        script = workDir.resolve("fake-soffice.sh");
        Files.writeString(script, "#!/bin/sh\n" + body);
        assertTrue(script.toFile().setExecutable(true));
    }
}
