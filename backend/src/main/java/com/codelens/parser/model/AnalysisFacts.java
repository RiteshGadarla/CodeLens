package com.codelens.parser.model;

import java.util.List;

public record AnalysisFacts(List<ParsedFile> files, List<EntityFact> entities, List<EdgeFact> edges) {
}
