package com.pm.pdfconverterapplication.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileNameUtilsTest {

    @Test
    void sanitizeFileNameStripsPathTraversal() {
        assertEquals("passwd", FileNameUtils.sanitizeFileName("../etc/passwd"));
    }

    @Test
    void getSafeExtensionHandlesMissingExtension() {
        assertEquals("", FileNameUtils.getSafeExtension("no-extension"));
    }

    @Test
    void getSafeBaseNameUsesSanitizedInput() {
        assertEquals("report", FileNameUtils.getSafeBaseName("..\\report.pdf"));
    }
}
