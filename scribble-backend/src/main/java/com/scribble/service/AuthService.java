package com.scribble.service;

import com.scribble.domain.user.UserEntity;
import com.scribble.dto.AuthResponse;
import com.scribble.dto.LoginRequest;
import com.scribble.dto.RegisterRequest;
import com.scribble.repository.UserRepository;
import com.scribble.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    // ── Register ──────────────────────────────────────────────

    public AuthResponse register(RegisterRequest request) {
        if(userRepository.existsByUsername(request.getUsername())) {
            throw new  IllegalStateException("Username already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("Email already registered");
        }

        UserEntity user = UserEntity.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .avatarColor(request.getAvatarColor())
                .role(UserEntity.Role.PLAYER)
                .build();

        userRepository.save(user);

        String token = jwtService.generateToken(
                user.getUsername(),
                user.getId().toString(),
                user.getRole().name()
        );

        log.info("Registered new user {}", user.getUsername());

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId().toString())
                .username(user.getUsername())
                .avatarColor(user.getAvatarColor())
                .role(user.getRole().name())
                .build();
    }

    // ── Login ─────────────────────────────────────────────────

    public AuthResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        String token = jwtService.generateToken(
                user.getUsername(),
                user.getId().toString(),
                user.getRole().name()
        );

        log.info("User logged in: {}", user.getUsername());

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId().toString())
                .username(user.getUsername())
                .avatarColor(user.getAvatarColor())
                .role(user.getRole().name())
                .build();
    }

    // ── Guest ─────────────────────────────────────────────────
    // No DB record — just a short-lived JWT with a random guest ID
    // Guest can play but stats aren't persisted

    public AuthResponse loginAsGuest(String preferredUsername) {
        String guestId = UUID.randomUUID().toString();
        String username = sanitizeGuestName(preferredUsername);

        // Make sure username doesn't clash with real users
        if (userRepository.existsByUsername(username)) {
            username = username + "_" + guestId.substring(0, 4);
        }

        String token = jwtService.generateGuestToken(username, guestId);

        log.info("Guest login: {}", username);

        return AuthResponse.builder()
                .token(token)
                .userId(guestId)
                .username(username)
                .avatarColor("#" + guestId.substring(0, 6).toUpperCase())
                .role("GUEST")
                .build();
    }

    private String sanitizeGuestName(String name) {
        if (name == null || name.isBlank()) return "Guest";
        // Strip anything that's not alphanumeric or underscore
        String clean = name.trim().replaceAll("[^a-zA-Z0-9_]", "");
        return clean.isEmpty() ? "Guest" : clean.substring(0, Math.min(clean.length(), 20));
    }
}
