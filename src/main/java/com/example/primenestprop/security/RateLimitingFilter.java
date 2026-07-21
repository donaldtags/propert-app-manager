package com.example.primenestprop.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sliding-window limiter kept in local memory - only effective per instance. A horizontally
 * scaled deployment needs a shared store (e.g. Redis) or this can be bypassed by routing
 * requests to different instances.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {
    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/forgot-password"
    );

    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Instant>> hits = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final int maxRequests;
    private final Duration window;

    public RateLimitingFilter(
            ObjectMapper objectMapper,
            @Value("${app.auth.rate-limit.max-requests:10}") int maxRequests,
            @Value("${app.auth.rate-limit.window-minutes:15}") long windowMinutes
    ) {
        this.objectMapper = objectMapper;
        this.maxRequests = maxRequests;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!LIMITED_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        String key = request.getRemoteAddr() + ":" + request.getRequestURI();
        Instant now = Instant.now();
        ConcurrentLinkedDeque<Instant> timestamps = hits.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(now.minus(window))) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                JsonErrorWriter.write(response, objectMapper, HttpStatus.TOO_MANY_REQUESTS,
                        "Too many requests. Please try again later.");
                return;
            }
            timestamps.addLast(now);
        }
        filterChain.doFilter(request, response);
    }
}
