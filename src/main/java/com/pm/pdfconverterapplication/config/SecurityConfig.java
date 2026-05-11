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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.cors.allowed-origin:http://localhost:8080}")
    private String allowedOrigin;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Set allowed origin from application.properties
        config.setAllowedOrigins(List.of(allowedOrigin));

        // Allow GET and POST methods (typical for PDF converter API)
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));

        // Allow all headers to support multipart file uploads
        config.setAllowedHeaders(List.of("*"));

        // Allow credentials if needed (cookies, authorization headers)
        config.setAllowCredentials(true);

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