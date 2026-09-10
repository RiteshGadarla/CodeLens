package com.codelens.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskRequestDto(@NotBlank @Size(min = 3, max = 2000) String question) {
}
