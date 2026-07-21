package com.example.primenestprop.escrow;

import static com.example.primenestprop.escrow.EscrowDtos.EscrowResponse;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/escrows")
public class EscrowController {
    private final EscrowService service;

    public EscrowController(EscrowService service) {
        this.service = service;
    }

    @PostMapping



    EscrowResponse create(@Valid @RequestBody EscrowDtos.CreateEscrowRequest request, @AuthenticationPrincipal AppUser currentUser) {
        return EscrowResponse.from(service.create(request, currentUser));
    }

    @GetMapping
    List<EscrowResponse> list(@RequestParam Long userId, @AuthenticationPrincipal AppUser currentUser) {
        return service.forUser(userId, currentUser).stream().map(EscrowResponse::from).toList();
    }

    @GetMapping("/admin")
    List<EscrowResponse> allForAdmin(@AuthenticationPrincipal AppUser currentUser) {
        return service.allForAdmin(currentUser).stream().map(EscrowResponse::from).toList();
    }

    @PatchMapping("/{id}/fund")
    EscrowResponse fund(
            @PathVariable Long id,
            @Valid @RequestBody EscrowDtos.FundEscrowRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return EscrowResponse.from(service.fund(id, request, currentUser));
    }

    @PatchMapping("/{id}/release")
    EscrowResponse release(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        return EscrowResponse.from(service.release(id, currentUser));
    }

    @PatchMapping("/{id}/dispute")
    EscrowResponse dispute(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        return EscrowResponse.from(service.dispute(id, currentUser));
    }
}
