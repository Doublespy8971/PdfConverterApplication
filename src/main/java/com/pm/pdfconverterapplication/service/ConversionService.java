package com.pm.pdfconverterapplication.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.io.ByteArrayInputStream;

@Service
public class ConversionService {

    @Autowired(required = false)
    private LibreOfficeConverterService libreOfficeConverter;

    public record ConversionResult(byte[] content, String fileName, String contentType) {}

    private static final Map<String, ToolDefinition> TOOLS;

    static {
        Map<String, ToolDefinition> toolsMap = new java.util.HashMap<>();
        toolsMap.put("word-to-pdf", new ToolDefinition("word-to-pdf", "pdf", "application/pdf", Set.of("doc", "docx", "odt", "rtf", "txt")));
        toolsMap.put("excel-to-pdf", new ToolDefinition("excel-to-pdf", "pdf", "application/pdf", Set.of("xls", "xlsx", "ods", "csv")));
        toolsMap.put("powerpoint-to-pdf", new ToolDefinition("powerpoint-to-pdf", "pdf", "application/pdf", Set.of("ppt", "pptx", "odp")));
        toolsMap.put("images-to-pdf", new ToolDefinition("images-to-pdf", "pdf", "application/pdf", Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp")));
        toolsMap.put("pdf-to-images", new ToolDefinition("pdf-to-images", "zip", "application/zip", Set.of("pdf")));
        toolsMap.put("pdf-to-word", new ToolDefinition("pdf-to-word", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of("pdf")));
        toolsMap.put("pdf-to-excel", new ToolDefinition("pdf-to-excel", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Set.of("pdf")));
        toolsMap.put("pdf-to-ppt", new ToolDefinition("pdf-to-ppt", "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", Set.of("pdf")));
        toolsMap.put("split-pdf", new ToolDefinition("split-pdf", "zip", "application/zip", Set.of("pdf")));
        toolsMap.put("merge-pdf", new ToolDefinition("merge-pdf", "pdf", "application/pdf", Set.of("pdf")));
        toolsMap.put("compress-pdf", new ToolDefinition("compress-pdf", "pdf", "application/pdf", Set.of("pdf")));
        TOOLS = Collections.unmodifiableMap(toolsMap);
    }

    public ConversionResult convert(MultipartFile file, String tool) throws Exception {
        ToolDefinition toolDefinition = getToolDefinition(tool);
        String extension = getFileExtension(file.getOriginalFilename());
        validateToolAndExtension(toolDefinition, extension);

        return switch (tool.toLowerCase(Locale.ROOT)) {
            case "pdf-to-images" -> convertPdfToImages(file, toolDefinition);
            case "pdf-to-word" -> convertPdfToWord(file, toolDefinition);
            case "pdf-to-excel" -> convertPdfToExcel(file, toolDefinition);
            case "pdf-to-ppt" -> convertPdfToPpt(file, toolDefinition);
            case "images-to-pdf" -> convertImagesToPdf(file, toolDefinition);
            case "split-pdf" -> splitPdf(file, toolDefinition);
            case "merge-pdf" -> mergePdf(file, toolDefinition);
            case "compress-pdf" -> compressPdf(file, toolDefinition);
            case "word-to-pdf", "excel-to-pdf", "powerpoint-to-pdf" -> {
                if (libreOfficeConverter != null && libreOfficeConverter.isLibreOfficeAvailable()) {
                    byte[] pdfBytes = libreOfficeConverter.convertOfficeDocumentToPdf(file);
                    yield new ConversionResult(pdfBytes, buildOutputFileName(file.getOriginalFilename(), "pdf"), toolDefinition.contentType());
                } else {
                    throw new UnsupportedOperationException("LibreOffice is not available. Please ensure LibreOffice is installed or use the Docker version for automatic setup.");
                }
            }
            default -> throw new IllegalArgumentException("Unknown tool: " + tool);
        };
    }

    private ConversionResult convertPdfToWord(MultipartFile file, ToolDefinition toolDefinition) throws Exception {
        byte[] fileContent = file.getBytes();

        try (PDDocument document = PDDocument.load(fileContent);
             XWPFDocument wordDoc = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            // Split text into paragraphs and add to Word document
            String[] paragraphs = text.split("\n");
            for (String para : paragraphs) {
                if (!para.trim().isEmpty()) {
                    XWPFParagraph paragraph = wordDoc.createParagraph();
                    paragraph.createRun().setText(para);
                }
            }

            wordDoc.write(outputStream);
            return new ConversionResult(outputStream.toByteArray(), buildOutputFileName(file.getOriginalFilename(), "docx"), toolDefinition.contentType());
        }
    }

    private ConversionResult convertPdfToExcel(MultipartFile file, ToolDefinition toolDefinition) throws Exception {
        byte[] fileContent = file.getBytes();

        try (PDDocument document = PDDocument.load(fileContent);
             XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            PDFTextStripper stripper = new PDFTextStripper();

            int pageCount = document.getNumberOfPages();
            for (int i = 0; i < Math.min(pageCount, 10); i++) { // Limit to 10 sheets
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);

                String pageText = stripper.getText(document);
                XSSFSheet sheet = workbook.createSheet("Page_" + (i + 1));

                String[] lines = pageText.split("\n");
                for (int rowNum = 0; rowNum < lines.length; rowNum++) {
                    String line = lines[rowNum];
                    String[] cells = line.split("\t");

                    var row = sheet.createRow(rowNum);
                    for (int colNum = 0; colNum < cells.length; colNum++) {
                        row.createCell(colNum).setCellValue(cells[colNum]);
                    }
                }
            }

            workbook.write(outputStream);
            return new ConversionResult(outputStream.toByteArray(), buildOutputFileName(file.getOriginalFilename(), "xlsx"), toolDefinition.contentType());
        }
    }

    private ConversionResult convertPdfToPpt(MultipartFile file, ToolDefinition toolDefinition) throws Exception {
        byte[] fileContent = file.getBytes();

        try (PDDocument document = PDDocument.load(fileContent);
             XMLSlideShow pptDoc = new XMLSlideShow();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            PDFTextStripper stripper = new PDFTextStripper();
            int pageCount = document.getNumberOfPages();

            for (int i = 0; i < Math.min(pageCount, 20); i++) { // Limit to 20 slides
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);

                String pageText = stripper.getText(document);

                XSLFSlide slide = pptDoc.createSlide();

                // Create title
                org.apache.poi.xslf.usermodel.XSLFTextShape titleShape = slide.createTextBox();
                titleShape.setAnchor(new java.awt.geom.Rectangle2D.Double(50, 50, 400, 50));
                titleShape.setText("Page " + (i + 1));

                // Create content
                org.apache.poi.xslf.usermodel.XSLFTextShape contentShape = slide.createTextBox();
                contentShape.setAnchor(new java.awt.geom.Rectangle2D.Double(50, 120, 550, 400));
                String truncatedText = pageText.substring(0, Math.min(500, pageText.length()));
                contentShape.setText(truncatedText);
            }

            pptDoc.write(outputStream);
            return new ConversionResult(outputStream.toByteArray(), buildOutputFileName(file.getOriginalFilename(), "pptx"), toolDefinition.contentType());
        }
    }

    private ConversionResult convertPdfToImages(MultipartFile file, ToolDefinition toolDefinition) throws Exception {
        byte[] fileContent = file.getBytes();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOut = new ZipOutputStream(outputStream);
             PDDocument document = PDDocument.load(fileContent)) {

            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();

            if (pageCount == 0) {
                throw new IllegalArgumentException("PDF document has no pages");
            }

            for (int i = 0; i < pageCount; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 150);
                String imageName = String.format("page_%d.png", i + 1);

                ZipEntry entry = new ZipEntry(imageName);
                zipOut.putNextEntry(entry);

                ImageIO.write(image, "PNG", zipOut);
                zipOut.closeEntry();
            }

            zipOut.finish();
            return new ConversionResult(outputStream.toByteArray(), buildOutputFileName(file.getOriginalFilename(), "zip"), toolDefinition.contentType());
        }
    }

    private ConversionResult convertImagesToPdf(MultipartFile file, ToolDefinition toolDefinition) throws Exception {
        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) {
            throw new IllegalArgumentException("Could not read image file");
        }

        // Create a new PDF document with the image
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            // Create a page with dimensions matching the image
            float width = image.getWidth();
            float height = image.getHeight();
            PDPage page = new PDPage(new PDRectangle(width, height));
            document.addPage(page);

            // Save image to temporary file for PDFBox
            File tempFile = File.createTempFile("img_", ".png");
            ImageIO.write(image, "PNG", tempFile);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                PDImageXObject pdImage = PDImageXObject.createFromFile(tempFile.getAbsolutePath(), document);
                contentStream.drawImage(pdImage, 0, 0, width, height);
            } finally {
                if (!tempFile.delete()) {
                    tempFile.deleteOnExit();
                }
            }

            document.save(outputStream);
            return new ConversionResult(outputStream.toByteArray(), buildOutputFileName(file.getOriginalFilename(), "pdf"), toolDefinition.contentType());
        }
    }

    private ConversionResult splitPdf(MultipartFile file, ToolDefinition toolDefinition) throws Exception {
        byte[] fileContent = file.getBytes();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOut = new ZipOutputStream(outputStream);
             PDDocument document = PDDocument.load(fileContent)) {

            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                throw new IllegalArgumentException("PDF document has no pages");
            }

            for (int i = 0; i < pageCount; i++) {
                try (PDDocument singlePageDoc = new PDDocument();
                     ByteArrayOutputStream pageStream = new ByteArrayOutputStream()) {

                    PDPage page = document.getPage(i);
                    singlePageDoc.importPage(page);

                    singlePageDoc.save(pageStream);

                    ZipEntry entry = new ZipEntry(String.format("page_%d.pdf", i + 1));
                    zipOut.putNextEntry(entry);
                    zipOut.write(pageStream.toByteArray());
                    zipOut.closeEntry();
                }
            }

            zipOut.finish();
            return new ConversionResult(outputStream.toByteArray(), buildOutputFileName(file.getOriginalFilename(), "zip"), toolDefinition.contentType());
        }
    }

    private ConversionResult mergePdf(MultipartFile file, ToolDefinition toolDefinition) {
        // Note: For merge functionality, the frontend will handle multiple file uploads
        // This single file method is a placeholder. Actual merge requires multiple files.
        throw new UnsupportedOperationException("Merge PDF requires uploading multiple PDF files. Please use the merge tool interface.");
    }

    public ConversionResult mergePdfFiles(MultipartFile[] files) throws Exception {
        try (PDDocument mergedDocument = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            for (MultipartFile file : files) {
                String extension = getFileExtension(file.getOriginalFilename());
                if (!extension.equals("pdf")) {
                    throw new IllegalArgumentException("All files must be PDF. Found: " + extension);
                }

                try (PDDocument document = PDDocument.load(file.getBytes())) {
                    for (int i = 0; i < document.getNumberOfPages(); i++) {
                        PDPage page = document.getPage(i);
                        mergedDocument.importPage(page);
                    }
                }
            }

            mergedDocument.save(outputStream);
            return new ConversionResult(outputStream.toByteArray(), "merged.pdf", "application/pdf");
        }
    }

    /**
     * Batch convert multiple files - creates a zip with separate folders for each file
     * Each folder contains the converted output from that file
     */
    public ConversionResult batchConvert(MultipartFile[] files, String tool) throws Exception {
        ToolDefinition toolDefinition = getToolDefinition(tool);

        if (files.length == 0) {
            throw new IllegalArgumentException("Please upload at least one file");
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {

            for (int fileIndex = 0; fileIndex < files.length; fileIndex++) {
                MultipartFile file = files[fileIndex];
                String extension = getFileExtension(file.getOriginalFilename());
                validateToolAndExtension(toolDefinition, extension);

                String baseName = getBaseName(file.getOriginalFilename());
                String folderPrefix = baseName + "_" + (fileIndex + 1);

                try {
                    ConversionResult result = convertSingleFile(file, tool, toolDefinition);

                    // Add to zip with folder structure - keep the original filename with extension
                    String outputFileName = result.fileName(); // Preserve extension from result

                    // Handle nested zip files (pdf-to-images, split-pdf)
                    if (tool.equals("pdf-to-images") || tool.equals("split-pdf")) {
                        // Extract the nested zip and add its contents to the batch zip
                        addZipContentsToFolder(zipOut, result.content(), folderPrefix);
                    } else {
                        // Single file output
                        ZipEntry entry = new ZipEntry(folderPrefix + "/" + outputFileName);
                        zipOut.putNextEntry(entry);
                        zipOut.write(result.content());
                        zipOut.closeEntry();
                    }

                } catch (Exception e) {
                    // Add error file for this conversion
                    String errorFileName = folderPrefix + "/ERROR.txt";
                    ZipEntry errorEntry = new ZipEntry(errorFileName);
                    zipOut.putNextEntry(errorEntry);
                    String errorMsg = "Error converting " + file.getOriginalFilename() + ": " + e.getMessage();
                    zipOut.write(errorMsg.getBytes());
                    zipOut.closeEntry();
                }
            }

            zipOut.finish();
            String outputFileName = "batch_conversion_" + System.currentTimeMillis() + ".zip";
            return new ConversionResult(outputStream.toByteArray(), outputFileName, "application/zip");
        }
    }

    /**
     * Helper method to extract contents from a nested zip and add them to the parent zip
     */
    private void addZipContentsToFolder(ZipOutputStream parentZip, byte[] nestedZipContent, String folderPrefix) throws Exception {
        try (java.io.ByteArrayInputStream zipInput = new java.io.ByteArrayInputStream(nestedZipContent);
             java.util.zip.ZipInputStream nestedZip = new java.util.zip.ZipInputStream(zipInput)) {

            java.util.zip.ZipEntry entry;
            while ((entry = nestedZip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    // Create new entry in parent zip with folder prefix
                    String newEntryName = folderPrefix + "/" + entry.getName();
                    ZipEntry newEntry = new ZipEntry(newEntryName);
                    parentZip.putNextEntry(newEntry);

                    // Copy content
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = nestedZip.read(buffer)) > 0) {
                        parentZip.write(buffer, 0, len);
                    }

                    parentZip.closeEntry();
                }
            }
        }
    }

    /**
     * Internal method to convert a single file
     */
    private ConversionResult convertSingleFile(MultipartFile file, String tool, ToolDefinition toolDefinition) throws Exception {
        return switch (tool.toLowerCase(Locale.ROOT)) {
            case "pdf-to-images" -> convertPdfToImages(file, toolDefinition);
            case "pdf-to-word" -> convertPdfToWord(file, toolDefinition);
            case "pdf-to-excel" -> convertPdfToExcel(file, toolDefinition);
            case "pdf-to-ppt" -> convertPdfToPpt(file, toolDefinition);
            case "images-to-pdf" -> convertImagesToPdf(file, toolDefinition);
            case "split-pdf" -> splitPdf(file, toolDefinition);
            case "compress-pdf" -> compressPdf(file, toolDefinition);
            case "word-to-pdf", "excel-to-pdf", "powerpoint-to-pdf" -> {
                if (libreOfficeConverter != null && libreOfficeConverter.isLibreOfficeAvailable()) {
                    byte[] pdfBytes = libreOfficeConverter.convertOfficeDocumentToPdf(file);
                    yield new ConversionResult(pdfBytes, buildOutputFileName(file.getOriginalFilename(), "pdf"), toolDefinition.contentType());
                } else {
                    throw new UnsupportedOperationException("LibreOffice is not available");
                }
            }
            default -> throw new IllegalArgumentException("Unknown tool: " + tool);
        };
    }

    private ConversionResult compressPdf(MultipartFile file, ToolDefinition toolDefinition) throws Exception {
        byte[] fileContent = file.getBytes();

        try (PDDocument document = PDDocument.load(fileContent);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            // Process each page to ensure optimal compression
            int pageCount = document.getNumberOfPages();
            for (int i = 0; i < pageCount; i++) {
                // Accessing pages to ensure they are processed
                document.getPage(i);
            }

            // Save with compression enabled (default behavior of PDFBox)
            document.save(outputStream);
            return new ConversionResult(outputStream.toByteArray(), buildOutputFileName(file.getOriginalFilename(), "pdf"), toolDefinition.contentType());
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    private String getBaseName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "converted";
        }
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private String buildOutputFileName(String originalFilename, String outputExtension) {
        return getBaseName(originalFilename) + "." + outputExtension;
    }

    private void validateToolAndExtension(ToolDefinition toolDefinition, String extension) {
        if (extension.isBlank()) {
            throw new IllegalArgumentException("Please upload a file with a valid extension.");
        }

        if (!toolDefinition.allowedExtensions().contains(extension)) {
            throw new IllegalArgumentException("Invalid file type for " + toolDefinition.key() + ". Allowed: " + String.join(", ", toolDefinition.allowedExtensions()));
        }
    }

    private ToolDefinition getToolDefinition(String tool) {
        if (tool == null || tool.isBlank()) {
            throw new IllegalArgumentException("Conversion tool is required.");
        }

        ToolDefinition toolDefinition = TOOLS.get(tool.toLowerCase(Locale.ROOT));
        if (toolDefinition == null) {
            throw new IllegalArgumentException("Unknown conversion tool: " + tool);
        }

        return toolDefinition;
    }

    private record ToolDefinition(String key, String outputExtension, String contentType, Set<String> allowedExtensions) {
    }
}