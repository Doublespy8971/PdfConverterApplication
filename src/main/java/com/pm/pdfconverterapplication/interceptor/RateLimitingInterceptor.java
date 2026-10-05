package com.pm.pdfconverterapplication.interceptor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class RateLimitingInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingInterceptor.class);
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]+$");

    // Caffeine cache: stores buckets per IP, evicts after 2 hours of inactivity
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(2, TimeUnit.HOURS)
            .build();
    private final boolean trustForwardedHeaders;
    private final Set<String> trustedProxies;
    private final ObjectMapper objectMapper;
    private final int requestsPerHour;

    @Autowired
    public RateLimitingInterceptor(
            @Value("${app.rate-limit.trust-forwarded-headers:false}") boolean trustForwardedHeaders,
            @Value("${app.rate-limit.trusted-proxies:}") String trustedProxies,
            ObjectMapper objectMapper,
            @Value("${app.rate-limit.requests-per-hour:15}") int requestsPerHour
    ) {
        if (requestsPerHour < 1) {
            throw new IllegalArgumentException("Rate limit must allow at least one request per hour");
        }
        this.trustForwardedHeaders = trustForwardedHeaders;
        this.trustedProxies = Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
        this.objectMapper = objectMapper;
        this.requestsPerHour = requestsPerHour;
    }

    public RateLimitingInterceptor(boolean trustForwardedHeaders, String trustedProxies, ObjectMapper objectMapper) {
        this(trustForwardedHeaders, trustedProxies, objectMapper, 15);
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        // Get client IP address
        String ipAddress = getClientIpAddress(request);

        // Get or create bucket for this IP using Caffeine cache
        // Automatically evicts after 2 hours of inactivity
        Bucket bucket = buckets.get(ipAddress, ip -> createNewBucket());

        // Try to consume 1 token
        if (!bucket.tryConsume(1)) {
            // Rate limit exceeded
            String method = request.getMethod();
            String path = request.getRequestURI();
            logger.warn("Rate limit exceeded for IP: {}, Method: {}, Path: {}", ipAddress, method, path);
            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json");
            response.setHeader("Retry-After", "3600");
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "error", "Rate limit exceeded. Maximum " + requestsPerHour
                            + " requests per hour allowed per IP. Retry after 60 minutes."
            )));
            return false;
        }

        return true;
    }

    @SuppressWarnings("deprecation")
    private Bucket createNewBucket() {
        Bandwidth bandwidth = Bandwidth.classic(requestsPerHour,
                Refill.intervally(requestsPerHour, Duration.ofHours(1)));
        return Bucket4j.builder()
                .addLimit(bandwidth)
                .build();
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!trustForwardedHeaders || !trustedProxies.contains(remoteAddr)) {
            return remoteAddr;
        }

        // Try X-Forwarded-For header first (for trusted proxies/load balancers)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            String[] forwardedAddresses = xForwardedFor.split(",");
            for (int i = forwardedAddresses.length - 1; i >= 0; i--) {
                String forwardedIp = forwardedAddresses[i].trim();
                if (trustedProxies.contains(forwardedIp)) {
                    continue;
                }
                if (isValidIpAddress(forwardedIp)) {
                    return forwardedIp;
                }
            }
        }

        // Try X-Real-IP header (common in Nginx)
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.trim();
        }

        return remoteAddr;
    }

    private boolean isValidIpAddress(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (IPV4.matcher(value).matches()) {
            return Arrays.stream(value.split("\\."))
                    .mapToInt(Integer::parseInt)
                    .allMatch(octet -> octet <= 255);
        }
        return value.contains(":") && IPV6.matcher(value).matches();
    }
}
