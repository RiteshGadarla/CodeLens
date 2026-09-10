package com.codelens.api.dto;

import java.util.List;
import java.util.Map;

// facts = deterministic graph context sent with the question; answer = llm
public record AskResponseDto(String answer, List<AiSourceDto> sources, Map<String, Object> facts, String model,
                             boolean cached, String generatedBy) {
}
