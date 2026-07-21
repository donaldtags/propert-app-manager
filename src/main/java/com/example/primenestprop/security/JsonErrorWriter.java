package com.example.primenestprop.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;

final class JsonErrorWriter {
    private JsonErrorWriter() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
