package com.mailflow.service;

import com.mailflow.exception.ApiException;
import com.mailflow.model.User;
import com.mailflow.repository.UserRepository;
import com.mailflow.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Direct port of server/routes/accounts.js — multi-account session
 * switching ("Account switcher" in the sidebar user section).
 */
@Service
public class AccountsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AccountsService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public List<User.ActiveSession> listSessions(User me) {
        return me.getActiveSessions();
    }

    public Map<String, Object> addAccount(User me, String email, String password, String label) {
        if (email == null || email.isBlank() || password == null || password.isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email and password required");

        User account = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(password, account.getPassword()))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");

        String token = jwtUtil.generateToken(account.getId());
        account.getActiveSessions().add(User.ActiveSession.builder()
                .token(token).label(label == null || label.isBlank() ? "Secondary" : label).build());
        userRepository.save(account);

        return Map.of(
                "message", "Account added", "token", token,
                "user", Map.of("id", account.getId(), "name", account.getName(), "email", account.getEmail(),
                        "role", account.getRole(), "avatar", account.getAvatar() == null ? "" : account.getAvatar())
        );
    }

    public void logoutSession(User me, String token) {
        me.getActiveSessions().removeIf(s -> s.getToken().equals(token));
        userRepository.save(me);
    }
}
