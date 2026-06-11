package com.example.primenestprop.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/logs")
public class FrontendLogController {
    private static final Logger log = LoggerFactory.getLogger(FrontendLogController.class);

    @PostMapping("/frontend")
    Map<String, Object> frontend(@Valid @RequestBody FrontendLogRequest request) {
        log.info("frontend_event level={} event={} route={} userId={} message={} metadata={}",
                request.level(),
                request.event(),
                request.route(),
                request.userId(),
                request.message(),
                request.metadata());
        return Map.of("received", true, "timestamp", Instant.now());
    }

    public record FrontendLogRequest(
            String level,
            @NotBlank String event,
            String route,
            Long userId,
            String message,
            Map<String, Object> metadata
    ) {
    }
}
