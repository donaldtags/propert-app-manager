package com.example.primenestprop.admin;

import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyPhoto;
import com.example.primenestprop.property.PropertyRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Heuristic fraud detection over Homestead's own listing data - no external ML model, no
 * fabricated "risk score". Every signal below is a plain, explainable rule an admin can verify
 * by eye:
 *  1. The same photo URL appears on two different properties (an image was reused or stolen).
 *  2. A price is a severe outlier against the median of directly comparable listings (same city,
 *     suburb, bedroom count, and listing type) - a common scam pattern is an unrealistically low
 *     price to draw inquiries.
 *  3. The same street address is used across properties from two different landlord accounts.
 */
@Service
public class FraudDetectionService {
    private static final int MIN_GROUP_SIZE_FOR_OUTLIER = 3;
    private static final BigDecimal LOW_OUTLIER_RATIO = new BigDecimal("0.4");
    private static final BigDecimal HIGH_OUTLIER_RATIO = new BigDecimal("2.5");

    private final PropertyRepository properties;

    public FraudDetectionService(PropertyRepository properties) {
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<FraudSignal> scan() {
        List<Property> all = properties.findAll();
        List<FraudSignal> signals = new ArrayList<>();

        signals.addAll(duplicatePhotoSignals(all));
        signals.addAll(priceOutlierSignals(all));
        signals.addAll(duplicateAddressSignals(all));

        return signals;
    }

    private List<FraudSignal> duplicatePhotoSignals(List<Property> all) {
        Map<String, List<Property>> byPhotoUrl = new java.util.HashMap<>();
        for (Property property : all) {
            for (PropertyPhoto photo : property.getPhotos()) {
                if (photo.getPhotoUrl() == null || photo.getPhotoUrl().isBlank()) continue;
                byPhotoUrl.computeIfAbsent(photo.getPhotoUrl(), k -> new ArrayList<>()).add(property);
            }
        }

        // A property may share several photo URLs with the same other properties - collapse all
        // of that into one "other property ids" set per property before emitting a single signal,
        // rather than one signal per shared URL.
        Map<Long, Property> propertyById = new java.util.LinkedHashMap<>();
        Map<Long, java.util.Set<Long>> othersByPropertyId = new java.util.HashMap<>();
        for (var entry : byPhotoUrl.entrySet()) {
            List<Property> withThisPhoto = entry.getValue().stream()
                    .collect(Collectors.toMap(Property::getId, p -> p, (a, b) -> a))
                    .values().stream().toList();
            if (withThisPhoto.size() < 2) continue;
            for (Property property : withThisPhoto) {
                propertyById.put(property.getId(), property);
                java.util.Set<Long> others = othersByPropertyId.computeIfAbsent(property.getId(), k -> new java.util.TreeSet<>());
                withThisPhoto.stream()
                        .map(Property::getId)
                        .filter(id -> !id.equals(property.getId()))
                        .forEach(others::add);
            }
        }

        List<FraudSignal> signals = new ArrayList<>();
        for (var entry : othersByPropertyId.entrySet()) {
            Property property = propertyById.get(entry.getKey());
            String others = entry.getValue().stream().map(id -> "#" + id).collect(Collectors.joining(", "));
            signals.add(new FraudSignal(
                    property.getId(),
                    property.getTitle(),
                    "DUPLICATE_PHOTO",
                    "HIGH",
                    "Shares a photo with " + others + " - possible reused/stolen image"
            ));
        }
        return signals;
    }

    private List<FraudSignal> priceOutlierSignals(List<Property> all) {
        Map<String, List<Property>> byComparableGroup = all.stream()
                .filter(p -> p.getPrice() != null)
                .collect(Collectors.groupingBy(p ->
                        p.getListingType() + "|" + p.getCity().toLowerCase() + "|" + p.getSuburb().toLowerCase() + "|" + p.getBedrooms()));

        List<FraudSignal> signals = new ArrayList<>();
        for (var entry : byComparableGroup.entrySet()) {
            List<Property> group = entry.getValue();
            if (group.size() < MIN_GROUP_SIZE_FOR_OUTLIER) continue;

            List<BigDecimal> sortedPrices = group.stream().map(Property::getPrice).sorted().toList();
            BigDecimal median = median(sortedPrices);
            if (median.signum() <= 0) continue;

            BigDecimal lowThreshold = median.multiply(LOW_OUTLIER_RATIO);
            BigDecimal highThreshold = median.multiply(HIGH_OUTLIER_RATIO);

            for (Property property : group) {
                if (property.getPrice().compareTo(lowThreshold) < 0) {
                    signals.add(new FraudSignal(
                            property.getId(),
                            property.getTitle(),
                            "PRICE_OUTLIER_LOW",
                            "MEDIUM",
                            "Priced " + property.getPrice() + " " + property.getCurrency()
                                    + " vs a median of " + median + " for comparable listings in " + property.getSuburb()
                                    + " - unusually cheap can indicate a scam listing"
                    ));
                } else if (property.getPrice().compareTo(highThreshold) > 0) {
                    signals.add(new FraudSignal(
                            property.getId(),
                            property.getTitle(),
                            "PRICE_OUTLIER_HIGH",
                            "LOW",
                            "Priced " + property.getPrice() + " " + property.getCurrency()
                                    + " vs a median of " + median + " for comparable listings in " + property.getSuburb()
                    ));
                }
            }
        }
        return signals;
    }

    private List<FraudSignal> duplicateAddressSignals(List<Property> all) {
        Map<String, List<Property>> byAddress = all.stream()
                .filter(p -> p.getAddress() != null && !p.getAddress().isBlank())
                .collect(Collectors.groupingBy(p ->
                        p.getAddress().trim().toLowerCase() + "|" + p.getCity().toLowerCase() + "|" + p.getSuburb().toLowerCase()));

        List<FraudSignal> signals = new ArrayList<>();
        for (var entry : byAddress.entrySet()) {
            List<Property> group = entry.getValue();
            long distinctLandlords = group.stream().map(p -> p.getLandlord().getId()).distinct().count();
            if (group.size() < 2 || distinctLandlords < 2) continue;

            for (Property property : group) {
                signals.add(new FraudSignal(
                        property.getId(),
                        property.getTitle(),
                        "DUPLICATE_ADDRESS",
                        "HIGH",
                        "Same address listed by " + distinctLandlords + " different landlord accounts"
                ));
            }
        }
        return signals;
    }

    private BigDecimal median(List<BigDecimal> sortedPrices) {
        int size = sortedPrices.size();
        if (size % 2 == 1) {
            return sortedPrices.get(size / 2);
        }
        return sortedPrices.get(size / 2 - 1)
                .add(sortedPrices.get(size / 2))
                .divide(new BigDecimal("2"), 2, java.math.RoundingMode.HALF_UP);
    }
}
