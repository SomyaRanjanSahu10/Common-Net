package com.mailflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Migrated from server/models/User.js (Mongoose UserSchema).
 * Password hashing (BCrypt) happens in AuthService/UserService, not in this
 * entity, since Spring Data has no pre-save hooks equivalent to Mongoose's
 * `pre('save')`.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    private String email;

    /** BCrypt hash. Never serialized back to clients — see UserResponseDto. */
    private String password;

    @Builder.Default
    private String role = "user"; // "user" | "admin"

    @Builder.Default
    private boolean isActive = true;

    private Instant lastLogin;

    // ── Profile (Feature 4) ──────────────────────────────────────────────
    @Builder.Default
    private String avatar = "";
    @Builder.Default
    private String designation = "";
    @Builder.Default
    private String department = "";
    @Builder.Default
    private String phone = "";
    @Builder.Default
    private String bio = "";

    // ── Multi-account sessions (Feature 5) ───────────────────────────────
    @Builder.Default
    private List<ActiveSession> activeSessions = new ArrayList<>();

    // ── Forgot password / OTP ────────────────────────────────────────────
    private String passwordResetToken;
    private Instant passwordResetExpires;
    private String passwordResetOTP;
    private Instant passwordResetOTPExp;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveSession {
        private String token;
        @Builder.Default
        private String label = "Primary";
        @Builder.Default
        private Instant createdAt = Instant.now();
    }
}
