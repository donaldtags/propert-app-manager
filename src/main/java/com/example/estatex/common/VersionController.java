package com.example.estatex.common;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Which commit this backend is running (Render sets RENDER_GIT_COMMIT on every deploy). Lets the
 * deployed-app test workflow wait until a new version is actually live before testing it.
 */
@RestController
public class VersionController {
    private final String commit;

    public VersionController(@Value("${RENDER_GIT_COMMIT:${GIT_COMMIT:dev}}") String commit) {
        this.commit = commit;
    }

    @GetMapping("/api/v1/version")
    ResponseEntity<Map<String, String>> version() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("service", "backend", "commit", commit));
    }
}
