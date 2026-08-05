package com.example.primenestprop.investment;

import com.example.primenestprop.property.ListingType;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyDtos;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserDtos;
import com.example.primenestprop.user.UserRepository;
import com.example.primenestprop.user.UserRole;
import com.example.primenestprop.user.UserService;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Populates a starter catalog of Southern African REITs with real backing property listings,
 * so the investments marketplace never shows up empty. Runs in every environment (unlike
 * DemoDataSeeder, which is opt-in demo data) since this is reference product catalog content,
 * not simulated user activity. Idempotent per REIT name, so adding a new entry here seeds just
 * that one on next boot without touching REITs that already exist.
 */
@Component
@Order(10)
class ReitCatalogSeeder implements CommandLineRunner {
    private static final String ISSUER_EMAIL = "capital@primenest.africa";

    private final ReitRepository reits;
    private final UserRepository userRepository;
    private final UserService userService;
    private final PropertyService propertyService;

    ReitCatalogSeeder(ReitRepository reits, UserRepository userRepository, UserService userService, PropertyService propertyService) {
        this.reits = reits;
        this.userRepository = userRepository;
        this.userService = userService;
        this.propertyService = propertyService;
    }

    @Override
    public void run(String... args) {
        AppUser issuer = userRepository.findByEmailIgnoreCase(ISSUER_EMAIL).orElseGet(() -> {
            AppUser created = userService.create(new UserDtos.CreateUserRequest(
                    "PrimeNest Capital Partners",
                    ISSUER_EMAIL,
                    "+263771000099",
                    "Rt7#" + UUID.randomUUID(),
                    "Zimbabwe",
                    Set.of(UserRole.LANDLORD, UserRole.AGENT)
            ));
            userService.verify(created.getId());
            return created;
        });

        seedReit(issuer,
                "Harare Residential Growth Fund", "Zimbabwe", "RESIDENTIAL",
                "Income-producing apartments and townhouses across Harare's northern suburbs.",
                new BigDecimal("10.00"), new BigDecimal("8.50"), "MEDIUM", new BigDecimal("50000"),
                "https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?w=1200&q=80", null,
                () -> List.of(
                        property(issuer, "Borrowdale Garden Apartments", ListingType.RENT, "Harare", "Borrowdale", "Zimbabwe",
                                2, 2, "650.00", "https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?w=1200&q=80"),
                        property(issuer, "Mount Pleasant Townhouse Row", ListingType.RENT, "Harare", "Mount Pleasant", "Zimbabwe",
                                3, 2, "780.00", "https://images.unsplash.com/photo-1568605114967-8130f3a36994?w=1200&q=80")
                ));

        seedReit(issuer,
                "Tigere Property Fund REIT", "Zimbabwe", "MIXED_USE",
                "ZSE-listed diversified property fund (ticker TIG). This Homestead product tracks the live ZSE "
                        + "quote for reference; your units are issued and settled in USD at the price below.",
                new BigDecimal("10.00"), new BigDecimal("9.00"), "MEDIUM", new BigDecimal("40000"),
                "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?w=1200&q=80", "TIG",
                () -> List.of(
                        property(issuer, "Msasa Mixed-Use Complex", ListingType.RENT, "Harare", "Msasa", "Zimbabwe",
                                0, 2, "1900.00", "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?w=1200&q=80")
                ));

        seedReit(issuer,
                "Revitus Property Opportunities REIT", "Zimbabwe", "COMMERCIAL",
                "ZSE-listed property opportunities fund (ticker REV). This Homestead product tracks the live ZSE "
                        + "quote for reference; your units are issued and settled in USD at the price below.",
                new BigDecimal("10.00"), new BigDecimal("9.30"), "MEDIUM", new BigDecimal("40000"),
                "https://images.unsplash.com/photo-1441986300917-64674bd600d8?w=1200&q=80", "REV",
                () -> List.of(
                        property(issuer, "Msasa Commercial Yard", ListingType.RENT, "Harare", "Msasa", "Zimbabwe",
                                0, 1, "1600.00", "https://images.unsplash.com/photo-1441986300917-64674bd600d8?w=1200&q=80")
                ));

        seedReit(issuer,
                "Bulawayo Industrial & Logistics Trust", "Zimbabwe", "INDUSTRIAL",
                "Warehousing and light-industrial parks serving Zimbabwe's second city.",
                new BigDecimal("25.00"), new BigDecimal("11.00"), "MEDIUM", new BigDecimal("20000"),
                "https://images.unsplash.com/photo-1553413077-190dd305871c?w=1200&q=80", null,
                () -> List.of(
                        property(issuer, "Belmont Logistics Park", ListingType.RENT, "Bulawayo", "Belmont", "Zimbabwe",
                                0, 2, "3200.00", "https://images.unsplash.com/photo-1553413077-190dd305871c?w=1200&q=80"),
                        property(issuer, "Kelvin Industrial Warehouse", ListingType.RENT, "Bulawayo", "Kelvin", "Zimbabwe",
                                0, 1, "2100.00", "https://images.unsplash.com/photo-1553028826-f4804a6dfd3f?w=1200&q=80")
                ));

        seedReit(issuer,
                "Cape Town Coastal Commercial REIT", "South Africa", "COMMERCIAL",
                "Retail and mixed commercial frontage across Cape Town's most walkable nodes.",
                new BigDecimal("50.00"), new BigDecimal("9.20"), "LOW", new BigDecimal("30000"),
                "https://images.unsplash.com/photo-1449844908441-8829872d2607?w=1200&q=80", null,
                () -> List.of(
                        property(issuer, "Sea Point Retail Terrace", ListingType.RENT, "Cape Town", "Sea Point", "South Africa",
                                0, 2, "1800.00", "https://images.unsplash.com/photo-1449844908441-8829872d2607?w=1200&q=80"),
                        property(issuer, "Woodstock Creative Offices", ListingType.RENT, "Cape Town", "Woodstock", "South Africa",
                                0, 3, "2400.00", "https://images.unsplash.com/photo-1541888946425-d81bb19240f5?w=1200&q=80")
                ));

        seedReit(issuer,
                "Sandton Office Fund", "South Africa", "COMMERCIAL",
                "Grade A office towers in Johannesburg's Sandton financial district.",
                new BigDecimal("75.00"), new BigDecimal("8.80"), "LOW", new BigDecimal("40000"),
                "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?w=1200&q=80", null,
                () -> List.of(
                        property(issuer, "Sandton Gateway Tower", ListingType.RENT, "Johannesburg", "Sandton", "South Africa",
                                0, 4, "5200.00", "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?w=1200&q=80"),
                        property(issuer, "Rosebank Business Park", ListingType.RENT, "Johannesburg", "Rosebank", "South Africa",
                                0, 3, "3600.00", "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?w=1200&q=80")
                ));

        seedReit(issuer,
                "Lusaka Residential Trust", "Zambia", "RESIDENTIAL",
                "Modern rental apartments serving Lusaka's growing professional class.",
                new BigDecimal("8.00"), new BigDecimal("9.50"), "MEDIUM", new BigDecimal("35000"),
                "https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?w=1200&q=80", null,
                () -> List.of(
                        property(issuer, "Kabulonga Residence Suites", ListingType.RENT, "Lusaka", "Kabulonga", "Zambia",
                                2, 2, "700.00", "https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?w=1200&q=80"),
                        property(issuer, "Roma Park Apartments", ListingType.RENT, "Lusaka", "Roma", "Zambia",
                                1, 1, "480.00", "https://images.unsplash.com/photo-1564013799919-ab600027ffc6?w=1200&q=80")
                ));

        seedReit(issuer,
                "Gaborone Diversified Property Fund", "Botswana", "MIXED_USE",
                "A blended residential, retail, and office portfolio across Gaborone's CBD.",
                new BigDecimal("15.00"), new BigDecimal("10.10"), "HIGH", new BigDecimal("15000"),
                "https://images.unsplash.com/photo-1518780664697-55e3ad937233?w=1200&q=80", null,
                () -> List.of(
                        property(issuer, "CBD Mixed-Use Plaza", ListingType.RENT, "Gaborone", "CBD", "Botswana",
                                0, 2, "1500.00", "https://images.unsplash.com/photo-1518780664697-55e3ad937233?w=1200&q=80"),
                        property(issuer, "Phakalane Garden Homes", ListingType.SALE, "Gaborone", "Phakalane", "Botswana",
                                4, 3, "185000.00", "https://images.unsplash.com/photo-1564013799919-ab600027ffc6?w=1200&q=80")
                ));
    }

    private Property property(AppUser issuer, String title, ListingType listingType, String city, String suburb,
            String country, int bedrooms, int bathrooms, String price, String photoUrl) {
        Property created = propertyService.create(new PropertyDtos.CreatePropertyRequest(
                title,
                "Institutionally managed asset held within a PrimeNest REIT portfolio.",
                listingType,
                city,
                suburb,
                null,
                country,
                bedrooms,
                bathrooms,
                new BigDecimal(price),
                "USD",
                null,
                null,
                true,
                false,
                false,
                false,
                null,
                false,
                false,
                false,
                false,
                false,
                issuer.getId(),
                issuer.getId(),
                List.of(photoUrl),
                null,
                null,
                null
        ));
        propertyService.verify(created.getId(), new PropertyDtos.VerifyPropertyRequest(issuer.getId(), "REIT portfolio asset, pre-verified"));
        return created;
    }

    private void seedReit(AppUser issuer, String name, String country, String propertyType, String description,
            BigDecimal unitPrice, BigDecimal projectedYield, String riskLevel, BigDecimal totalUnits,
            String coverImageUrl, String tickerSymbol, Supplier<List<Property>> backingProperties) {
        if (reits.existsByName(name)) {
            return;
        }
        Reit reit = new Reit();
        reit.setName(name);
        reit.setDescription(description);
        reit.setMarket(country);
        reit.setUnitPrice(unitPrice);
        reit.setProjectedAnnualYield(projectedYield);
        reit.setRiskLevel(riskLevel);
        reit.setVexEligible(country.equals("Zimbabwe"));
        reit.setTotalUnits(totalUnits);
        reit.setUnitsSold(totalUnits.multiply(new BigDecimal("0.35")));
        reit.setPropertyType(propertyType);
        reit.setCoverImageUrl(coverImageUrl);
        reit.setTickerSymbol(tickerSymbol);
        reit.setProperties(new LinkedHashSet<>(backingProperties.get()));
        reits.save(reit);
    }
}
