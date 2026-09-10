package com.codelens.api.dto;

public record AiSourceDto(int ref, String path, String label, String qualifiedName, int startLine, int endLine,
                          double score, Long entityId) {
}
