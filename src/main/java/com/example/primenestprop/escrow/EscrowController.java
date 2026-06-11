package com.example.primenestprop.escrow;

import static com.example.primenestprop.escrow.EscrowDtos.EscrowResponse;

import jakarta.validation.Valid;
import java.util.List;
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
    EscrowResponse create(@Valid @RequestBody EscrowDtos.CreateEscrowRequest request) {
        return EscrowResponse.from(service.create(request));
    }

    @GetMapping
    List<EscrowResponse> list(@RequestParam Long userId) {
        return service.forUser(userId).stream().map(EscrowResponse::from).toList();
    }

    @PatchMapping("/{id}/fund")
    EscrowResponse fund(@PathVariable Long id) {
        return EscrowResponse.from(service.fund(id));
    }

    @PatchMapping("/{id}/release")
    EscrowResponse release(@PathVariable Long id) {
        return EscrowResponse.from(service.release(id));
    }

    @PatchMapping("/{id}/dispute")
    EscrowResponse dispute(@PathVariable Long id) {
        return EscrowResponse.from(service.dispute(id));
    }
}
