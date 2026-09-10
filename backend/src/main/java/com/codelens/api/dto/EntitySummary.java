package com.codelens.api.dto;

import com.codelens.domain.EntityKind;
import com.codelens.domain.Stereotype;

public record EntitySummary(long id, String name, String label, String qualifiedName, EntityKind kind,
                            Stereotype role, String filePath, String module, Integer startLine, Integer endLine,
                            Double riskScore) {
}
