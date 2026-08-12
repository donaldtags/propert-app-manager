package com.example.primenestprop.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${app.storage.maintenance-photos:storage/maintenance-photos}")
    private String maintenancePhotoStorage;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Property photos are served by PropertyPhotoRedirectController (S3-backed), not from local disk.
        registry.addResourceHandler("/uploads/maintenance-photos/**")
                .addResourceLocations("file:" + java.nio.file.Path.of(maintenancePhotoStorage).toAbsolutePath() + "/");
    }
}
