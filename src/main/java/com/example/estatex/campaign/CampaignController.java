package com.example.estatex.campaign;

import com.example.estatex.user.AppUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orgs/{slug}")
public class CampaignController {
    private final CampaignService service;

    public CampaignController(CampaignService service) {
        this.service = service;
    }

    @GetMapping("/listings/{propertyId}/campaign")
    ResponseEntity<CampaignDtos.CampaignResponse> current(@PathVariable String slug, @PathVariable Long propertyId,
                                                          @AuthenticationPrincipal AppUser user) {
        CampaignDtos.CampaignResponse c = service.current(slug, propertyId, user);
        return c == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(c);
    }

    @PostMapping("/listings/{propertyId}/campaign")
    CampaignDtos.CampaignResponse start(@PathVariable String slug, @PathVariable Long propertyId, @AuthenticationPrincipal AppUser user) {
        return service.start(slug, propertyId, user);
    }

    @PutMapping("/campaign-tasks/{taskId}")
    CampaignDtos.CampaignResponse setTask(@PathVariable String slug, @PathVariable Long taskId,
                                          @Valid @RequestBody CampaignDtos.SetTaskRequest request, @AuthenticationPrincipal AppUser user) {
        return service.setTask(slug, taskId, request, user);
    }
}
