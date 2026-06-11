package com.example.primenestprop.property;

import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class PropertyLocationBackfill implements CommandLineRunner {
    private final PropertyRepository properties;
    private final PropertyService propertyService;

    PropertyLocationBackfill(PropertyRepository properties, PropertyService propertyService) {
        this.properties = properties;
        this.propertyService = propertyService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<Property> missingLocations = properties.findAll().stream()
                .filter(property -> property.getLatitude() == null || property.getLongitude() == null)
                .toList();
        for (Property property : missingLocations) {
            PropertyService.Coordinates coordinates = propertyService.coordinatesFor(
                    property.getCity(),
                    property.getSuburb(),
                    property.getLatitude(),
                    property.getLongitude()
            );
            property.setLatitude(coordinates.latitude());
            property.setLongitude(coordinates.longitude());
        }
    }
}
