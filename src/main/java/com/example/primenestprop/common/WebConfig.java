package com.example.primenestprop.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${app.storage.property-photos:storage/property-photos}")
    private String propertyPhotoStorage;

    @Value("${app.storage.maintenance-photos:storage/maintenance-photos}")
    private String maintenancePhotoStorage;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/property-photos/**")
                .addResourceLocations("file:" + java.nio.file.Path.of(propertyPhotoStorage).toAbsolutePath() + "/");
        registry.addResourceHandler("/uploads/maintenance-photos/**")
                .addResourceLocations("file:" + java.nio.file.Path.of(maintenancePhotoStorage).toAbsolutePath() + "/");
    }
}
