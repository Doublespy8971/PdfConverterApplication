package com.pm.pdfconverterapplication.controller;

import com.pm.pdfconverterapplication.service.ConversionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/api/convert")
@CrossOrigin(origins = "*")
public class ConverterController {

    private final ConversionService conversionService;

    public ConverterController(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @PostMapping("/batch/{tool}")
    public ResponseEntity<?> batchConvertFiles(@PathVariable String tool, @RequestParam("files") MultipartFile[] files) {
        try {
            if (files.length == 0) {
                return ResponseEntity.badRequest().body("Please upload at least one file");
            }

            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    return ResponseEntity.badRequest().body("One or more files are empty");
                }
            }

            ConversionService.ConversionResult result = conversionService.batchConvert(files, tool);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(result.contentType()));
            headers.setContentDispositionFormData("attachment", result.fileName());
            return new ResponseEntity<>(result.content(), headers, HttpStatus.OK);

        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Batch conversion failed: " + e.getMessage());
        }
    }

    @PostMapping("/{tool}")
    public ResponseEntity<?> convertFile(@PathVariable String tool, @RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File is empty");
            }

            ConversionService.ConversionResult result = conversionService.convert(file, tool);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(result.contentType()));
            headers.setContentDispositionFormData("attachment", result.fileName());
            return new ResponseEntity<>(result.content(), headers, HttpStatus.OK);

        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Conversion failed: " + e.getMessage());
        }
    }

    @PostMapping("/merge-pdf")
    public ResponseEntity<?> mergePdfFiles(@RequestParam("files") MultipartFile[] files) {
        try {
            if (files.length < 2) {
                return ResponseEntity.badRequest().body("Please upload at least 2 PDF files to merge");
            }

            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    return ResponseEntity.badRequest().body("One or more files are empty");
                }
            }

            ConversionService.ConversionResult result = conversionService.mergePdfFiles(files);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(result.contentType()));
            headers.setContentDispositionFormData("attachment", result.fileName());
            return new ResponseEntity<>(result.content(), headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Merge failed: " + e.getMessage());
        }
    }
}