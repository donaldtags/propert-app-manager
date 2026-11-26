package com.example.estatex.analytics.capture;

import com.example.estatex.user.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Public: the browser reports a listing view or a search. See {@link MarketplaceEventService} for the filtering. */
@RestController
class MarketplaceEventController {
    private final MarketplaceEventService service;

    MarketplaceEventController(MarketplaceEventService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/marketplace-events")
    Map<String, Boolean> track(@RequestBody MarketplaceEventService.Track request,
                               @AuthenticationPrincipal AppUser user, HttpServletRequest http) {
        boolean counted = service.track(request, user == null ? null : user.getId(), http.getRemoteAddr(),
                http.getHeader("User-Agent"));
        return Map.of("counted", counted);
    }
}
