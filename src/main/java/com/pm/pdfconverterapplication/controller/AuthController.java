package com.pm.pdfconverterapplication.controller;

import com.pm.pdfconverterapplication.model.AuthRequest;
import com.pm.pdfconverterapplication.model.AuthResponse;
import com.pm.pdfconverterapplication.security.JwtUtil;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JwtUtil jwtUtil;

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody AuthRequest request) {
        // Dummy auth for MVP
        String token = jwtUtil.generateToken(request.getUsername());
        return new AuthResponse(token);
    }
}