package com.example.primenestprop.common;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Uploads files to an S3-compatible bucket (Railway/Tigris) and serves them back via short-lived
 * presigned URLs, since the bucket is private and objects aren't served with a stable public URL.
 */
@Service
public class ObjectStorageService {
    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public ObjectStorageService(
            @Value("${app.storage.s3.endpoint:}") String endpoint,
            @Value("${app.storage.s3.access-key:}") String accessKey,
            @Value("${app.storage.s3.secret-key:}") String secretKey,
            @Value("${app.storage.s3.bucket:}") String bucket,
            @Value("${app.storage.s3.region:auto}") String region
    ) {
        this.bucket = bucket;
        if (endpoint.isBlank()) {
            this.client = null;
            this.presigner = null;
            return;
        }
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        this.client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(credentials)
                .region(Region.of(region))
                .forcePathStyle(false)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(credentials)
                .region(Region.of(region))
                .build();
    }

    public boolean isConfigured() {
        return client != null;
    }

    public void put(String key, MultipartFile file) {
        try {
            client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType(file.getContentType()).build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } catch (IOException ex) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Could not store uploaded file");
        }
    }

    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    public String presignedUrl(String key) {
        var request = presigner.presignGetObject(builder -> builder
                .signatureDuration(Duration.ofHours(6))
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build()));
        return request.url().toString();
    }
}