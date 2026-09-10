package com.codelens.parser.model;

import com.codelens.domain.EdgeType;

public record EdgeFact(String sourceQn, String targetQn, EdgeType type, int weight, String filePath) {
}
