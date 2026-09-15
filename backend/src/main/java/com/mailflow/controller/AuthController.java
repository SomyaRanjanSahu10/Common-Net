package com.mailflow.controller;

import com.mailflow.dto.*;
import com.mailflow.security.UserPrincipal;
import com.mailflow.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Migrated from server/routes/auth.js. Endpoint paths, HTTP methods, and
 * response shapes are preserved 1:1 for frontend compatibility.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse resp = authService.register(request);
        return Map.of("message", "Registration successful", "token", resp.getToken(), "user", resp.getUser());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse resp = authService.login(request);
        return Map.of("message", "Login successful", "token", resp.getToken(), "user", resp.getUser());
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal UserPrincipal principal) {
        return Map.of("user", authService.me(principal.getUser()));
    }

    @PostMapping("/forgot-password")
    public Map<String, Object> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return Map.of("message", "If that email exists, an OTP has been sent.");
    }

    @PostMapping("/verify-otp")
    public Map<String, Object> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        String resetToken = authService.verifyOtp(request);
        return Map.of("message", "OTP verified", "resetToken", resetToken);
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return Map.of("message", "Password reset successful. You can now log in.");
    }
}
