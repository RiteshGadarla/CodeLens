package com.codelens.api.dto;

import com.codelens.domain.ReportKind;
import com.codelens.impact.ImpactResult;

import java.time.Instant;
import java.util.List;

public record ReportDto(long id, Long entityId, ReportKind kind, ImpactResult impact, String summary,
                        List<AiSourceDto> sources, String model, Instant createdAt) {
}
