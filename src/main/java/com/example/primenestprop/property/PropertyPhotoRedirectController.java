package com.example.primenestprop.property;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.common.ObjectStorageService;
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
        try {
            return ResponseEntity.ok()
                    .contentType(MediaTypeFactory.getMediaType(filename).orElse(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM))
                    .contentLength(Files.size(file))
                    .body(new FileSystemResource(file));
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read photo");
        }
    }
}