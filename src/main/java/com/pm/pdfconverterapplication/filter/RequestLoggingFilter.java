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

/**
 * Logging filter for diagnostics on request origin, path, and headers.
 * Useful for debugging CORS and deployment-specific issues.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // Log for API requests to diagnose CORS and routing issues
        String method = request.getMethod();
        String path = request.getRequestURI();
        String origin = request.getHeader("Origin");
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        String xRealIp = request.getHeader("X-Real-IP");
        String host = request.getHeader("Host");

        if (path.startsWith("/api/")) {
            logger.debug("API Request - Method: {}, Path: {}, Origin: {}, Host: {}, X-Forwarded-For: {}, X-Real-IP: {}, RemoteAddr: {}",
                    method, path, origin != null ? origin : "none", host, xForwardedFor, xRealIp, request.getRemoteAddr());
        }

        filterChain.doFilter(request, response);
    }
}

