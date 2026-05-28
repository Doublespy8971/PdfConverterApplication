package com.pm.pdfconverterapplication.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Diagnostic endpoint for CORS and request troubleshooting.
 * Helps identify configuration and origin mismatches on deployment.
 */
@RestController
@RequestMapping("/api/diagnostics")
public class DiagnosticsController {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosticsController.class);

    @Value("${app.cors.allowed-origin:http://localhost:8080}")
    private String allowedOrigin;

    /**
     * Returns the configured CORS allowed origins and current request details.
     * Use this to verify that the app is reading the environment variable correctly.
     */
    @GetMapping("/cors-config")
    public ResponseEntity<?> getCorsConfig(
            HttpServletRequest request,
            @RequestHeader(value = "Origin", required = false) String origin) {

        Map<String, Object> response = new HashMap<>();

        // Parse configured origins
        List<String> configuredOrigins = Arrays.stream(allowedOrigin.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());

        response.put("configuredOrigins", configuredOrigins.isEmpty() ? List.of("http://localhost:8080") : configuredOrigins);
        response.put("rawAllowedOriginValue", allowedOrigin);
        response.put("incomingOrigin", origin != null ? origin : "none (no Origin header in request)");
        response.put("requestUrl", request.getRequestURL().toString());
        response.put("requestServerName", request.getServerName());
        response.put("requestServerPort", request.getServerPort());
        response.put("remoteAddr", request.getRemoteAddr());
        response.put("xForwardedFor", request.getHeader("X-Forwarded-For") != null ? request.getHeader("X-Forwarded-For") : "not present");
        response.put("xRealIp", request.getHeader("X-Real-IP") != null ? request.getHeader("X-Real-IP") : "not present");
        response.put("status", "configured");

        // Check if incoming origin matches configured origins
        if (origin != null && !configuredOrigins.isEmpty()) {
            boolean matches = configuredOrigins.stream().anyMatch(o -> o.equals(origin) || "*".equals(o));
            response.put("originMatches", matches);
            if (!matches) {
                response.put("warning", "Incoming origin does NOT match configured origins. This may cause CORS errors.");
                logger.warn("CORS origin mismatch: incoming='{}', configured={}", origin, configuredOrigins);
            }
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Simple health check to test if the server accepts the request.
     * If this returns 200, the server is reachable and not rate-limited on this endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "healthy", "timestamp", System.currentTimeMillis()));
    }

    /**
     * Test POST endpoint with minimal form data.
     * Use this to diagnose CSRF and CORS issues on POST requests.
     */
    @PostMapping("/test-post")
    public ResponseEntity<?> testPost(
            HttpServletRequest request,
            @RequestHeader(value = "Origin", required = false) String origin) {

        Map<String, Object> response = new HashMap<>();
        response.put("method", "POST");
        response.put("path", request.getRequestURI());
        response.put("origin", origin != null ? origin : "none");
        response.put("contentType", request.getContentType());
        response.put("timestamp", System.currentTimeMillis());
        response.put("message", "POST request accepted - CSRF protection is not blocking this endpoint");

        logger.info("Diagnostic POST request succeeded - CSRF is properly configured for /api/**");
        return ResponseEntity.ok(response);
    }

    /**
     * Test OPTIONS preflight request.
     * Browsers send OPTIONS before POST for cross-origin requests.
     * If this works, preflight should succeed.
     */
    @RequestMapping(value = "/test-options", method = org.springframework.web.bind.annotation.RequestMethod.OPTIONS)
    public ResponseEntity<?> testOptions(HttpServletRequest request) {
        logger.info("Diagnostic OPTIONS request received on {}", request.getRequestURI());
        return ResponseEntity.ok().header("X-Custom-Header", "OPTIONS-OK").build();
    }
}



