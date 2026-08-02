package com.selfsync.api.service;

import com.selfsync.api.dto.ApiDtos.*;
import com.selfsync.api.model.AuthProvider;
import com.selfsync.api.model.User;
import com.selfsync.api.repository.UserRepository;
import com.selfsync.api.security.JwtService;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final FirebaseTokenService firebaseTokenService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            FirebaseTokenService firebaseTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.firebaseTokenService = firebaseTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered.");
        }
        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setProvider(AuthProvider.SELF);
        user.setEmailVerified(false);
        userRepository.save(user);
        return toAuth(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));
        if (!StringUtils.hasText(user.getPasswordHash())
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }
        return toAuth(user);
    }

    /**
     * Verifies a Firebase ID token, then maps/creates a local user row (provider mapping).
     */
    @Transactional
    public AuthResponse loginWithFirebase(FirebaseLoginRequest request) {
        FirebaseTokenService.FirebaseIdentity identity = firebaseTokenService.verifyIdToken(request.idToken());

        User user = userRepository.findByFirebaseUid(identity.uid())
                .or(() -> userRepository.findByEmail(identity.email()))
                .orElseGet(User::new);

        boolean isNew = user.getEmail() == null;
        user.setEmail(identity.email());
        user.setFirebaseUid(identity.uid());
        user.setEmailVerified(identity.emailVerified());
        if (StringUtils.hasText(identity.displayName())) {
            user.setFullName(identity.displayName());
        } else if (!StringUtils.hasText(user.getFullName())) {
            user.setFullName(identity.email().split("@")[0]);
        }
        if (StringUtils.hasText(identity.photoUrl())) {
            user.setAvatarUrl(identity.photoUrl());
        }
        user.setProvider(mapProvider(identity.signInProvider()));
        if (isNew || user.getPasswordHash() == null) {
            // OAuth users have no local password; store a random unusable hash placeholder when brand new
            if (!StringUtils.hasText(user.getPasswordHash())) {
                user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            }
        }
        userRepository.save(user);
        return toAuth(user);
    }

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase();
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getProvider() == AuthProvider.SELF) {
                String token = UUID.randomUUID().toString().replace("-", "");
                user.setResetToken(token);
                user.setResetTokenExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
                userRepository.save(user);
                // In production, email this link. For local/dev we return token in logs via message.
            }
        });
        // Always same response to avoid email enumeration
        User maybe = userRepository.findByEmail(email).orElse(null);
        if (maybe != null && maybe.getProvider() == AuthProvider.SELF && maybe.getResetToken() != null) {
            return new MessageResponse(
                    "If an account exists, a reset link was created. Dev token: " + maybe.getResetToken()
                            + " (use /reset-password?token=...)");
        }
        return new MessageResponse("If an account exists for that email, a reset link has been sent.");
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByResetToken(request.token())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token."));
        if (user.getResetTokenExpiresAt() == null || user.getResetTokenExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setResetToken(null);
        user.setResetTokenExpiresAt(null);
        userRepository.save(user);
        return new MessageResponse("Your password has been reset successfully.");
    }

    @Transactional
    public UserDto me(UUID userId) {
        return toUserDto(requireUser(userId));
    }

    @Transactional
    public UserDto updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = requireUser(userId);
        user.setFullName(request.fullName().trim());
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber().trim());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().trim());
        }
        return toUserDto(user);
    }

    private AuthProvider mapProvider(String signInProvider) {
        if (signInProvider == null) return AuthProvider.FIREBASE;
        String p = signInProvider.toLowerCase();
        if (p.contains("google")) return AuthProvider.GOOGLE;
        if (p.contains("github")) return AuthProvider.GITHUB;
        if (p.contains("password")) return AuthProvider.FIREBASE;
        return AuthProvider.FIREBASE;
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
    }

    private AuthResponse toAuth(User user) {
        String token = jwtService.createToken(user.getId(), user.getEmail(), user.getFullName());
        return new AuthResponse(token, toUserDto(user));
    }

    private UserDto toUserDto(User user) {
        return new UserDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getCreatedAt(),
                user.getProvider() == null ? AuthProvider.SELF.name() : user.getProvider().name(),
                user.getAvatarUrl(),
                user.getPhoneNumber(),
                user.isEmailVerified(),
                user.getFirebaseUid());
    }
}
