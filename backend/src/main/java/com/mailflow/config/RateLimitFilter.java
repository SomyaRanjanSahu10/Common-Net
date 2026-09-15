package com.mailflow.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal in-memory sliding-window-ish rate limiter, replacing
 * express-rate-limit's generalLimiter (200 req / 15 min, all routes) and
 * authLimiter (20 req / 15 min, /api/auth/*) from middleware/security.js.
 *
 * For a multi-instance deployment this should be backed by Redis instead of
 * an in-memory map, but functionally it matches the original single-instance
 * Node app's behavior.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 15 * 60 * 1000L;
    private static final int GENERAL_MAX = 200;
    private static final int AUTH_MAX = 20;

    private final ConcurrentHashMap<String, Bucket> generalBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> authBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String ip = clientIp(request);
        boolean isAuthRoute = request.getRequestURI().startsWith("/api/auth/");

        if (isAuthRoute && !allow(authBuckets, ip, AUTH_MAX)) {
            reject(response, "Too many auth attempts, please try again later.");
            return;
        }
        if (!allow(generalBuckets, ip, GENERAL_MAX)) {
            reject(response, "Too many requests, please try again later.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean allow(ConcurrentHashMap<String, Bucket> buckets, String key, int max) {
        long now = System.currentTimeMillis();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(now));
        synchronized (bucket) {
            if (now - bucket.windowStart > WINDOW_MS) {
                bucket.windowStart = now;
                bucket.count.set(0);
            }
            return bucket.count.incrementAndGet() <= max;
        }
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank()) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }

    private static class Bucket {
        volatile long windowStart;
        final AtomicInteger count = new AtomicInteger(0);
        Bucket(long start) { this.windowStart = start; }
    }
}
