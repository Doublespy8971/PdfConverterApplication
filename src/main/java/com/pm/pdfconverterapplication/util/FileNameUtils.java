package com.pm.pdfconverterapplication.util;

import java.nio.file.Paths;

public final class FileNameUtils {

    private FileNameUtils() {
    }

    public static String getSafeExtension(String originalName) {
        String sanitized = sanitizeFileName(originalName);
        int dotIndex = sanitized.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == sanitized.length() - 1) {
            return "";
        }
        return sanitized.substring(dotIndex + 1).toLowerCase();
    }

    public static String getSafeBaseName(String originalName) {
        String sanitized = sanitizeFileName(originalName);
        int dotIndex = sanitized.lastIndexOf('.');
        if (dotIndex > 0) {
            return sanitized.substring(0, dotIndex);
        }
        return sanitized.isBlank() ? "converted" : sanitized;
    }

    public static String sanitizeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "file";
        }

        String baseName = Paths.get(originalName).getFileName().toString(); // add this line

        String sanitized = baseName
                .replaceAll("[\\r\\n\\t]", "_")
                .replaceAll("[\\\\/]", "_")
                .replaceAll("[^A-Za-z0-9._-]", "_");

        if (sanitized.isBlank()) {
            return "file";
        }

        return sanitized;
    }
}
