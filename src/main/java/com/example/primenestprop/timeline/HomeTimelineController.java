package com.example.primenestprop.timeline;

import com.example.primenestprop.user.AppUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/timeline")
public class HomeTimelineController {
    private final HomeTimelineService service;

    public HomeTimelineController(HomeTimelineService service) {
        this.service = service;
    }

    @GetMapping("/mine")
    List<TimelineDtos.TimelineEvent> mine(@AuthenticationPrincipal AppUser currentUser) {
        return service.forTenant(currentUser);
    }
}
