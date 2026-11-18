package com.example.estatex.intake;

import com.example.estatex.user.AppUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.file.Files;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/orgs/{slug}")
public class IntakeController {
    private final IntakeService service;

    public IntakeController(IntakeService service) {
        this.service = service;
    }

    @GetMapping("/listings/{propertyId}/inspection")
    IntakeDtos.InspectionResponse inspection(@PathVariable String slug, @PathVariable Long propertyId, @AuthenticationPrincipal AppUser user) {
        return service.inspection(slug, propertyId, user);
    }

    @PutMapping("/listings/{propertyId}/inspection")
    IntakeDtos.InspectionResponse saveInspection(@PathVariable String slug, @PathVariable Long propertyId,
                                                 @Valid @RequestBody IntakeDtos.SaveInspectionRequest request,
                                                 @AuthenticationPrincipal AppUser user) {
        return service.saveInspection(slug, propertyId, request, user);
    }

    @PostMapping(value = "/listings/{propertyId}/inspection/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    IntakeDtos.PhotoResponse addPhoto(@PathVariable String slug, @PathVariable Long propertyId,
                                      @RequestPart("file") MultipartFile file,
                                      @RequestParam(required = false) String caption,
                                      @RequestParam(required = false) String itemKey,
                                      @RequestParam(required = false) String clientId,
                                      @AuthenticationPrincipal AppUser user) {
        InspectionPhoto p = service.addPhoto(slug, propertyId, file, caption, itemKey, clientId, user);
        return new IntakeDtos.PhotoResponse(p.getId(), p.getClientId(), p.getCaption(), p.getItemKey(), p.getContentType(), p.getCreatedAt());
    }

    @GetMapping("/inspection-photos/{photoId}")
    ResponseEntity<Resource> photo(@PathVariable String slug, @PathVariable Long photoId, @AuthenticationPrincipal AppUser user) {
        IntakeService.PhotoFile f = service.photo(slug, photoId, user);
        if (f.location().redirectUrl() != null) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(f.location().redirectUrl())).build();
        }
        if (!Files.exists(f.location().localPath())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(new FileSystemResource(f.location().localPath()));
    }

    @DeleteMapping("/inspection-photos/{photoId}")
    void deletePhoto(@PathVariable String slug, @PathVariable Long photoId, @AuthenticationPrincipal AppUser user) {
        service.deletePhoto(slug, photoId, user);
    }

    @GetMapping("/listings/{propertyId}/pricing")
    IntakeDtos.PricingSuggestion pricing(@PathVariable String slug, @PathVariable Long propertyId, @AuthenticationPrincipal AppUser user) {
        return service.pricing(slug, propertyId, user);
    }

    @PostMapping("/listings/{propertyId}/pricing")
    IntakeDtos.SavedRecommendation saveRecommendation(@PathVariable String slug, @PathVariable Long propertyId,
                                                      @Valid @RequestBody IntakeDtos.SaveRecommendationRequest request,
                                                      @AuthenticationPrincipal AppUser user) {
        return service.saveRecommendation(slug, propertyId, request, user);
    }
}
