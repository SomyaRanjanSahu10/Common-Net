package com.mailflow.service;

import com.mailflow.dto.*;
import com.mailflow.exception.ApiException;
import com.mailflow.model.User;
import com.mailflow.repository.UserRepository;
import com.mailflow.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Direct port of server/routes/auth.js business logic (route handlers just
 * delegate here now — see AuthController). Behavior, status codes, and
 * response shapes are kept 1:1 with the original.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final MailService mailService;
    private final EmailTemplates templates;
    private final ActivityLogService activityLogService;

    private static final SecureRandom RNG = new SecureRandom();

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                        MailService mailService, EmailTemplates templates, ActivityLogService activityLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.mailService = mailService;
        this.templates = templates;
        this.activityLogService = activityLogService;
    }

    public AuthResponse register(RegisterRequest req) {
        String email = req.getEmail().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "User with this email already exists");
        }

        User user = User.builder()
                .name(req.getName())
                .email(email)
                .password(passwordEncoder.encode(req.getPassword()))
                .role("user")
                .isActive(true)
                .build();
        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId());

        mailService.sendAsyncBestEffort(user.getEmail(), "Welcome to Common Net!",
                templates.welcomeEmail(user.getName(), user.getEmail()));

        activityLogService.log(user.getId(), "REGISTER", user.getEmail(), true);

        return new AuthResponse(token, UserResponse.from(user));
    }

    public AuthResponse login(LoginRequest req) {
        String email = req.getEmail().toLowerCase().trim();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            activityLogService.log(user.getId(), "LOGIN", "failed password", false);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account suspended. Please contact support.");
        }

        user.setLastLogin(Instant.now());
        userRepository.save(user);

        activityLogService.log(user.getId(), "LOGIN", "", true);

        String token = jwtUtil.generateToken(user.getId());
        return new AuthResponse(token, UserResponse.from(user));
    }

    public UserResponse me(User user) {
        return UserResponse.from(user);
    }

    /** Step 1: always responds the same way to prevent email enumeration, mirroring the Node route. */
    public void forgotPassword(ForgotPasswordRequest req) {
        Optional<User> userOpt = userRepository.findByEmail(req.getEmail().toLowerCase().trim());
        if (userOpt.isEmpty()) return; // controller sends the generic message regardless

        User user = userOpt.get();
        String otp = String.valueOf(100000 + RNG.nextInt(900000));
        Instant otpExpiry = Instant.now().plusSeconds(10 * 60);

        String rawToken = randomHex(32);
        String hashedToken = sha256Hex(rawToken);

        user.setPasswordResetOTP(otp);
        user.setPasswordResetOTPExp(otpExpiry);
        user.setPasswordResetToken(hashedToken);
        user.setPasswordResetExpires(Instant.now().plusSeconds(15 * 60));
        userRepository.save(user);

        try {
            mailService.send(user.getEmail(), "Common Net — Password Reset OTP",
                    templates.forgotPasswordOTP(user.getName(), otp));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send reset email. Please try again.");
        }
    }

    public String verifyOtp(VerifyOtpRequest req) {
        User user = userRepository.findByEmail(req.getEmail().toLowerCase().trim())
                .filter(u -> req.getOtp().equals(u.getPasswordResetOTP()))
                .filter(u -> u.getPasswordResetOTPExp() != null && u.getPasswordResetOTPExp().isAfter(Instant.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired OTP"));

        String rawToken = randomHex(32);
        String hashedToken = sha256Hex(rawToken);

        user.setPasswordResetOTP(null);
        user.setPasswordResetOTPExp(null);
        user.setPasswordResetToken(hashedToken);
        user.setPasswordResetExpires(Instant.now().plusSeconds(15 * 60));
        userRepository.save(user);

        return rawToken;
    }

    public void resetPassword(ResetPasswordRequest req) {
        String hashedToken = sha256Hex(req.getResetToken());
        User user = userRepository.findByPasswordResetToken(hashedToken)
                .filter(u -> u.getPasswordResetExpires() != null && u.getPasswordResetExpires().isAfter(Instant.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Reset token is invalid or has expired"));

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpires(null);
        user.setPasswordResetOTP(null);
        user.setPasswordResetOTPExp(null);
        userRepository.save(user);

        mailService.sendAsyncBestEffort(user.getEmail(), "Common Net — Password Changed",
                templates.passwordChanged(user.getName()));
    }

    private static String randomHex(int numBytes) {
        byte[] bytes = new byte[numBytes];
        RNG.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
