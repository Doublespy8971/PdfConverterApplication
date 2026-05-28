package com.pm.pdfconverterapplication.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Comprehensive debug filter for 403 and CORS issues.
 * Logs request/response details to help diagnose security and CORS problems.
 */
@Component
public class DebugFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(DebugFilter.class);

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");

        // Log all /api/** requests with headers
        if (path.startsWith("/api/")) {
            logger.debug("=== INCOMING REQUEST ===");
            logger.debug("Path: {} {}", method, path);
            logger.debug("Origin: {}", origin != null ? origin : "none");
            logger.debug("Referer: {}", referer != null ? referer : "none");
            logger.debug("Remote IP: {}", request.getRemoteAddr());
            logger.debug("X-Forwarded-For: {}", request.getHeader("X-Forwarded-For") != null ? request.getHeader("X-Forwarded-For") : "none");

            // Log request headers relevant to CORS and CSRF
            String cookieHeader = request.getHeader("Cookie");
            String csrfTokenHeader = request.getHeader("X-CSRF-TOKEN");
            String contentType = request.getContentType();
            logger.debug("Content-Type: {}", contentType);
            logger.debug("Cookie present: {}", cookieHeader != null ? "yes" : "no");
            logger.debug("X-CSRF-TOKEN present: {}", csrfTokenHeader != null ? "yes" : "no");
        }

        // Capture the response status
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Log response details for /api/** requests
            if (path.startsWith("/api/")) {
                int status = response.getStatus();
                String corsHeader = response.getHeader("Access-Control-Allow-Origin");
                String varyHeader = response.getHeader("Vary");

                logger.debug("=== OUTGOING RESPONSE ===");
                logger.debug("Status: {}", status);
                logger.debug("Access-Control-Allow-Origin: {}", corsHeader != null ? corsHeader : "NOT SET");
                logger.debug("Vary: {}", varyHeader != null ? varyHeader : "NOT SET");
                logger.debug("Content-Type: {}", response.getContentType());

                // Log warning for 4xx/5xx errors
                if (status >= 400) {
                    logger.warn("ERROR RESPONSE - Status: {}, Path: {} {}, Origin: {}, CORS-Header: {}",
                            status, method, path, origin != null ? origin : "none", corsHeader != null ? corsHeader : "MISSING");
                }
            }
        }
    }
}

