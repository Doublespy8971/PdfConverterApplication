package com.pm.pdfconverterapplication.security;

import org.springframework.stereotype.Component;

/**
 * JWT utilities - currently not used as authentication is disabled
 */
@Component
public class JwtUtil {

    public String generateToken(String username) {
        return "";
    }

    public String extractUsername(String token) {
        return "";
    }

    public boolean validateToken(String token) {
        return true;
    }
}