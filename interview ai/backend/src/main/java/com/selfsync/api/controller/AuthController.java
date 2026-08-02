package com.selfsync.api.controller;

import com.selfsync.api.dto.ApiDtos.*;
import com.selfsync.api.service.AuthService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/firebase")
    public AuthResponse firebase(@Valid @RequestBody FirebaseLoginRequest request) {
        return authService.loginWithFirebase(request);
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    @GetMapping("/me")
    public UserDto me(Authentication authentication) {
        return authService.me((UUID) authentication.getPrincipal());
    }

    @PutMapping("/profile")
    public UserDto updateProfile(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile((UUID) authentication.getPrincipal(), request);
    }
}
