package com.pm.pdfconverterapplication.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${app.cors.allowed-origin:http://localhost:8080}")
    private String allowedOrigin;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Log environment variable for diagnosis
        String envVar = System.getenv("APP_CORS_ALLOWED_ORIGIN");
        logger.info("==== CORS Configuration Debug ====");
        logger.info("Environment variable APP_CORS_ALLOWED_ORIGIN: {}", envVar != null ? envVar : "NOT SET (using default)");
        logger.info("Injected @Value allowedOrigin: {}", allowedOrigin);
        logger.info("====================================");

        CorsConfiguration config = new CorsConfiguration();

        // Support comma-separated list in the property and allow origin patterns (wildcards)
        List<String> origins = Arrays.stream(allowedOrigin.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());

        if (origins.isEmpty()) {
            // fallback to localhost
            origins = List.of("http://localhost:8080");
        }

        logger.info("Configured CORS allowed origins: {}", origins);

        // If a wildcard is explicitly configured, use allowed origin patterns and disable credentials
        if (origins.size() == 1 && ("*".equals(origins.getFirst()) || "http://*".equals(origins.getFirst()))) {
            config.setAllowedOriginPatterns(List.of("*"));
            config.setAllowCredentials(false);
        } else {
            // Use explicit allowed origins and allow credentials (cookies/auth headers)
            config.setAllowedOrigins(origins);
            config.setAllowCredentials(true);
        }

        // Allow methods commonly used by the API
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));

        // Allow all headers to support multipart file uploads
        config.setAllowedHeaders(List.of("*"));


        // Cache preflight response for 1 hour
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // Apply CORS to all API endpoints
        source.registerCorsConfiguration("/api/**", config);

        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // This app is intentionally unauthenticated and intended for public use.
        // Authentication is not required for any endpoints.
        // Security is enforced through:
        // 1. Rate limiting (RateLimitingInterceptor) - limits 15 requests/hour per IP
        // 2. CSRF protection for browser UI - protects state-changing operations
        // 3. CORS - restricted to configured allowed-origin (configurable per environment)
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))  // Protect browser UI, allow API calls
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))  // Use centralized CORS config
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }
}