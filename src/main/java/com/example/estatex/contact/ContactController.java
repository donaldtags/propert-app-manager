package com.example.estatex.contact;

import com.example.estatex.contact.ContactDtos.ContactResponse;
import com.example.estatex.user.AppUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orgs/{slug}/contacts")
public class ContactController {
    private final ContactService service;

    public ContactController(ContactService service) {
        this.service = service;
    }

    @GetMapping
    List<ContactResponse> search(@PathVariable String slug, @RequestParam(required = false) String q,
                                 @RequestParam(required = false) ContactType type,
                                 @RequestParam(defaultValue = "200") int limit, @AuthenticationPrincipal AppUser user) {
        return service.search(slug, q, type, limit, user).stream().map(ContactResponse::from).toList();
    }

    @PostMapping
    ContactResponse create(@PathVariable String slug, @Valid @RequestBody ContactDtos.CreateContactRequest request,
                           @AuthenticationPrincipal AppUser user) {
        return ContactResponse.from(service.create(slug, request, user));
    }

    @GetMapping("/{id}")
    ContactDtos.ContactDetail detail(@PathVariable String slug, @PathVariable Long id, @AuthenticationPrincipal AppUser user) {
        return service.detail(slug, id, user);
    }

    @PatchMapping("/{id}")
    ContactResponse update(@PathVariable String slug, @PathVariable Long id,
                           @Valid @RequestBody ContactDtos.UpdateContactRequest request, @AuthenticationPrincipal AppUser user) {
        return ContactResponse.from(service.update(slug, id, request, user));
    }

    @PutMapping("/{id}/qualification")
    ContactResponse qualify(@PathVariable String slug, @PathVariable Long id,
                            @Valid @RequestBody ContactDtos.QualificationRequest request, @AuthenticationPrincipal AppUser user) {
        return ContactResponse.from(service.qualify(slug, id, request, user));
    }
}
