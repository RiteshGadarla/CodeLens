package com.codelens.api.dto;

public record SourceDto(String path, int startLine, int endLine, String code, String language) {
}
