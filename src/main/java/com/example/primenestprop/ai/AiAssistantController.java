package com.example.primenestprop.ai;

import com.example.primenestprop.property.PropertyDtos.PropertyResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiAssistantController {
    private final AiAssistantService service;

    public AiAssistantController(AiAssistantService service) {
        this.service = service;
    }

    @PostMapping("/property-search")
    AiDtos.AiPropertyAnswer propertySearch(@Valid @RequestBody AiDtos.AiPropertyQuery request) {
        List<PropertyResponse> matches = service.search(request.query()).stream()
                .map(PropertyResponse::from)
                .toList();
        return new AiDtos.AiPropertyAnswer(service.answer(request.query(), matches.size()), matches);
    }
}
