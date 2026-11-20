package com.example.estatex.property;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.ObjectStorageService;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Property photos are uploaded to a private S3-compatible bucket (see {@link ObjectStorageService})
 * when one is configured, so the stored photoUrl points here and this redirects to a freshly-signed,
 * short-lived URL rather than serving a permanent public link. Falls back to serving straight off
 * local disk when no bucket is configured (e.g. local dev without S3 env vars set).
 */
@RestController
public class PropertyPhotoRedirectController {
    private final ObjectStorageService storage;
    private final Path photoStorageRoot;

    public PropertyPhotoRedirectController(
            ObjectStorageService storage,
            @Value("${app.storage.property-photos:storage/property-photos}") String photoStorageRoot
    ) {
        this.storage = storage;
        this.photoStorageRoot = Path.of(photoStorageRoot);
    }

    @GetMapping("/uploads/property-photos/{propertyId}/{filename}")
    ResponseEntity<?> serve(@PathVariable Long propertyId, @PathVariable String filename) {
        if (storage.isConfigured()) {
            String url = storage.presignedUrl(propertyId + "/" + filename);
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
        }
        Path file = photoStorageRoot.resolve(String.valueOf(propertyId)).resolve(filename);
        if (!Files.isRegularFile(file)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Photo not found");
        }
        // Only ever answer as an image. Older uploads kept the uploader's file name, so a file
        // called "x.html" must not be served as a web page from the API's own domain.
        org.springframework.http.MediaType type = MediaTypeFactory.getMediaType(filename)
                .filter(t -> "image".equals(t.getType()) && !"svg+xml".equals(t.getSubtype()))
                .orElse(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
        try {
            return ResponseEntity.ok()
                    .contentType(type)
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy", "default-src 'none'; sandbox")
                    .contentLength(Files.size(file))
                    .body(new FileSystemResource(file));
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read photo");
        }
    }
}