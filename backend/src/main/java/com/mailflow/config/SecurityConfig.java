package com.mailflow.config;

import com.mailflow.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Replaces:
 *  - server.js `helmet()` (security headers)
 *  - server.js `cors({ origin: CLIENT_URL, credentials: true })`
 *  - middleware/auth.js `protect` (wired via JwtAuthFilter) and `adminOnly`
 *    (wired here via requestMatchers(...).hasRole("ADMIN"))
 *
 * BCrypt is used for password hashing (matches bcryptjs on the Node side).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${mailflow.cors.client-url:http://localhost:3000}")
    private String clientUrl;

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10); // matches bcryptjs genSalt(10)
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // stateless JWT API, no cookies/forms involved
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                    .contentTypeOptions(withDefaults -> {})
                    .frameOptions(frame -> frame.deny())
                    .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                    .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true))
            )
            .authorizeHttpRequests(auth -> auth
                    // Public — mirrors what has no `protect` middleware in the Node routes
                    .requestMatchers(
                            "/api/auth/register", "/api/auth/login",
                            "/api/auth/forgot-password", "/api/auth/verify-otp", "/api/auth/reset-password",
                            "/api/health", "/api/calendar/holidays/**",
                            "/ws/**"
                    ).permitAll()
                    .requestMatchers("/uploads/**").permitAll() // static file serving, same as Node's express.static
                    // Admin-only — mirrors routes/admin.js `router.use(protect, adminOnly)`
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    // Everything else requires a valid JWT — mirrors `protect` on the rest of the routes
                    .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(clientUrl));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
