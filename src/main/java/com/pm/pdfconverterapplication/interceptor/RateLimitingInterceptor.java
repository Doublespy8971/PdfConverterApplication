package com.pm.pdfconverterapplication.interceptor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitingInterceptor implements HandlerInterceptor {

    // Caffeine cache: stores buckets per IP, evicts after 2 hours of inactivity
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(2, TimeUnit.HOURS)
            .build();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Get client IP address
        String ipAddress = getClientIpAddress(request);

        // Get or create bucket for this IP using Caffeine cache
        // Automatically evicts after 2 hours of inactivity
        Bucket bucket = buckets.get(ipAddress, ip -> createNewBucket());

        // Try to consume 1 token
        if (!bucket.tryConsume(1)) {
            // Rate limit exceeded
            response.setStatus(429); // Too Many Requests
            response.getWriter().write("{\"error\": \"Rate limit exceeded. Maximum 15 requests per hour allowed.\"}");
            response.setContentType("application/json");
            return false;
        }

        return true;
    }

    private Bucket createNewBucket() {
        // Capacity of 15 tokens, refilling 15 tokens every 1 hour
        Bandwidth bandwidth = Bandwidth.classic(15, Refill.intervally(15, Duration.ofHours(1)));
        return Bucket4j.builder()
                .addLimit(bandwidth)
                .build();
    }

    private String getClientIpAddress(HttpServletRequest request) {
        // Try X-Forwarded-For header first (for proxies/load balancers)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }

        // Try X-Real-IP header (common in Nginx)
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        // Fallback to direct remote address
        return request.getRemoteAddr();
    }
}


