package com.example.primenestprop.property;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserRole;
import com.example.primenestprop.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PropertyService {
    private static final Logger log = LoggerFactory.getLogger(PropertyService.class);
    private static final long MAX_PHOTO_SIZE = 8L * 1024L * 1024L;
    private static final Set<String> ALLOWED_PHOTO_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final Set<UserRole> LISTING_ROLES = Set.of(
            UserRole.LANDLORD, UserRole.AGENT, UserRole.DEVELOPER, UserRole.PRIVATE
    );

    private final PropertyRepository properties;
    private final PropertyPhotoRepository photos;
    private final UserService users;
    private final Path photoStorageRoot;
    private final String publicBaseUrl;

    public PropertyService(
            PropertyRepository properties,
            PropertyPhotoRepository photos,
            UserService users,
            @Value("${app.storage.property-photos:storage/property-photos}") String photoStorageRoot,
            @Value("${app.public-base-url:http://localhost:8081}") String publicBaseUrl
    ) {
        this.properties = properties;
        this.photos = photos;
        this.users = users;
        this.photoStorageRoot = Path.of(photoStorageRoot);
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public Property create(PropertyDtos.CreatePropertyRequest request) {
        AppUser landlord = users.require(request.landlordId());
        if (landlord.getRoles().stream().noneMatch(LISTING_ROLES::contains)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "landlordId must belong to a landlord, agent, developer, or private seller");
        }

        Property property = new Property();
        property.setTitle(request.title());
        property.setDescription(request.description());
        property.setListingType(request.listingType());
        property.setStatus(PropertyStatus.AVAILABLE);
        property.setCity(request.city());
        property.setSuburb(request.suburb());
        property.setAddress(request.address());
        property.setCountry(request.country() == null || request.country().isBlank() ? "Zimbabwe" : request.country());
        property.setBedrooms(request.bedrooms());
        property.setBathrooms(request.bathrooms());
        property.setPrice(request.price());
        property.setCurrency(request.currency() == null || request.currency().isBlank() ? "USD" : request.currency());
        Coordinates coordinates = coordinatesFor(request.city(), request.suburb(), request.latitude(), request.longitude());
        property.setLatitude(coordinates.latitude());
        property.setLongitude(coordinates.longitude());
        property.setDiasporaFriendly(request.diasporaFriendly());
        property.setEscrowRequired(request.escrowRequired());
        property.setLandlord(landlord);
        if (request.agentId() != null) {
            AppUser agent = users.require(request.agentId());
            if (!agent.getRoles().contains(UserRole.AGENT)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "agentId must belong to an agent user");
            }
            property.setAgent(agent);
        }
        Property saved = properties.save(property);
        savePhotoUrls(saved, requestPhotoUrls(request));
        return saved;
    }

    public Property require(Long id) {
        return properties.findWithPhotosById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Property not found"));
    }

    public List<Property> search(ListingType listingType, String city, String suburb, BigDecimal maxPrice, Integer bedrooms) {
        return properties.search(listingType, blankToNull(city), blankToNull(suburb), maxPrice, bedrooms);
    }

    public List<Property> forLandlord(Long landlordId) {
        return properties.findByLandlord(users.require(landlordId));
    }

    public void submitInquiry(Long id, PropertyDtos.InquiryRequest request) {
        Property property = require(id);
        log.info("Inquiry for property '{}' (id={}): from {} <{}> phone={} - {}",
                property.getTitle(), id, request.name(), request.email(), request.phone(), request.message());
    }

    @Transactional
    public Property uploadPhotos(Long id, List<MultipartFile> files) {
        Property property = require(id);
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least one photo is required");
        }
        try {
            Files.createDirectories(photoStorageRoot.resolve(String.valueOf(id)));
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not prepare photo storage");
        }

        int nextSortOrder = property.getPhotos().size();
        for (MultipartFile file : files) {
            validatePhoto(file);
            String originalName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                    ? "photo"
                    : Path.of(file.getOriginalFilename()).getFileName().toString();
            String storageName = UUID.randomUUID() + "-" + originalName;
            Path target = photoStorageRoot.resolve(String.valueOf(id)).resolve(storageName);
            try {
                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store uploaded photo");
            }
            PropertyPhoto photo = new PropertyPhoto();
            photo.setProperty(property);
            photo.setStorageKey(target.toString());
            photo.setPhotoUrl(publicBaseUrl + "/uploads/property-photos/" + id + "/" + storageName);
            photo.setSortOrder(nextSortOrder++);
            photo.setPrimaryPhoto(property.getPhotos().isEmpty() && nextSortOrder == 1);
            photos.save(photo);
            property.getPhotos().add(photo);
        }
        return property;
    }

    @Transactional
    public Property verify(Long id, PropertyDtos.VerifyPropertyRequest request) {
        AppUser verifier = users.require(request.verifierId());
        if (!verifier.getRoles().contains(UserRole.AGENT) && !verifier.getRoles().contains(UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only agents or admins can verify properties");
        }
        Property property = require(id);
        property.setVerificationStatus(VerificationStatus.VERIFIED);
        property.setVerifiedAt(Instant.now());
        return property;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private List<String> requestPhotoUrls(PropertyDtos.CreatePropertyRequest request) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        addAll(urls, request.photoUrls());
        addAll(urls, request.imageUrls());
        addAll(urls, request.photos());
        return urls.stream().toList();
    }

    private void addAll(Set<String> urls, List<String> values) {
        if (values == null) {
            return;
        }
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .forEach(urls::add);
    }

    private void savePhotoUrls(Property property, List<String> urls) {
        int sortOrder = property.getPhotos().size();
        for (String url : urls) {
            PropertyPhoto photo = new PropertyPhoto();
            photo.setProperty(property);
            photo.setPhotoUrl(url);
            photo.setStorageKey(url);
            photo.setSortOrder(sortOrder++);
            photo.setPrimaryPhoto(property.getPhotos().isEmpty());
            photos.save(photo);
            property.getPhotos().add(photo);
        }
    }

    private void validatePhoto(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Uploaded photo is empty");
        }
        if (file.getSize() > MAX_PHOTO_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Photo size must be 8MB or less");
        }
        if (!ALLOWED_PHOTO_TYPES.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only PNG, JPEG, and WebP photos are allowed");
        }
    }

    Coordinates coordinatesFor(String city, String suburb, BigDecimal latitude, BigDecimal longitude) {
        if (latitude != null && longitude != null) {
            return new Coordinates(latitude, longitude);
        }
        String key = (suburb == null ? "" : suburb.trim().toLowerCase()) + ","
                + (city == null ? "" : city.trim().toLowerCase());
        return switch (key) {
            case "borrowdale,harare" -> new Coordinates(new BigDecimal("-17.742"), new BigDecimal("31.096"));
            case "avondale,harare" -> new Coordinates(new BigDecimal("-17.796"), new BigDecimal("31.035"));
            case "mount pleasant,harare" -> new Coordinates(new BigDecimal("-17.764"), new BigDecimal("31.053"));
            case "newlands,harare" -> new Coordinates(new BigDecimal("-17.803"), new BigDecimal("31.071"));
            case "hillside,bulawayo" -> new Coordinates(new BigDecimal("-20.187"), new BigDecimal("28.606"));
            case "victoria falls town," -> new Coordinates(new BigDecimal("-17.932"), new BigDecimal("25.831"));
            case "victoria falls town,victoria falls" -> new Coordinates(new BigDecimal("-17.932"), new BigDecimal("25.831"));
            case "murambi,mutare" -> new Coordinates(new BigDecimal("-18.970"), new BigDecimal("32.669"));
            default -> new Coordinates(latitude, longitude);
        };
    }

    record Coordinates(BigDecimal latitude, BigDecimal longitude) {
    }
}
