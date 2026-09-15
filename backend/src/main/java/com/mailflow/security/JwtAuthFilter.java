package com.mailflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailflow.model.User;
import com.mailflow.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Direct port of middleware/auth.js `protect`:
 *   - reads "Authorization: Bearer <token>"
 *   - verifies with JWT_SECRET
 *   - loads the user (minus password) and attaches to the request context
 *   - returns 401 JSON (not a redirect) on any failure, exactly like the
 *     Express middleware did.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        // No Authorization header at all — let it through unauthenticated;
        // Spring Security's authorization rules decide if the endpoint needs auth.
        // (WebSocket handshake also carries the token as a query param — handled in WebSocketConfig.)
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        if (!jwtUtil.isValid(token)) {
            writeUnauthorized(response, "Not authorized, token invalid");
            return;
        }

        try {
            String userId = jwtUtil.extractUserId(token);
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                writeUnauthorized(response, "User not found");
                return;
            }

            UserPrincipal principal = new UserPrincipal(userOpt.get());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            writeUnauthorized(response, "Not authorized, token invalid");
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        Map<String, String> body = new HashMap<>();
        body.put("message", message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
