package com.example.primenestprop.pipeline;

import com.example.primenestprop.pipeline.PipelineDtos.CardResponse;
import com.example.primenestprop.user.AppUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orgs/{slug}/pipeline")
public class PipelineController {
    private final PipelineService service;

    public PipelineController(PipelineService service) {
        this.service = service;
    }

    @GetMapping
    List<CardResponse> board(@PathVariable String slug, @AuthenticationPrincipal AppUser user) {
        return service.board(slug, user);
    }

    @PostMapping
    CardResponse create(@PathVariable String slug, @Valid @RequestBody PipelineDtos.CreateCardRequest request,
                        @AuthenticationPrincipal AppUser user) {
        return service.response(service.create(slug, request, user));
    }

    @PatchMapping("/{id}")
    CardResponse update(@PathVariable String slug, @PathVariable Long id,
                        @Valid @RequestBody PipelineDtos.UpdateCardRequest request, @AuthenticationPrincipal AppUser user) {
        return service.response(service.update(slug, id, request, user));
    }

    @PostMapping("/{id}/move")
    CardResponse move(@PathVariable String slug, @PathVariable Long id,
                      @Valid @RequestBody PipelineDtos.MoveRequest request, @AuthenticationPrincipal AppUser user) {
        return service.response(service.move(slug, id, request, user));
    }
}
