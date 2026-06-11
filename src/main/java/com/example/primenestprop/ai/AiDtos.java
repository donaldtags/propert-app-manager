package com.example.primenestprop.ai;

import com.example.primenestprop.property.PropertyDtos.PropertyResponse;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class AiDtos {
    private AiDtos() {
    }

    public record AiPropertyQuery(@NotBlank String query) {
    }

    public record AiPropertyAnswer(String answer, List<PropertyResponse> matches) {
    }
}
