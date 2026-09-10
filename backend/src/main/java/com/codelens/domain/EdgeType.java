package com.codelens.domain;

// source depends on target
public enum EdgeType {
    IMPORTS,
    EXTENDS,
    IMPLEMENTS,
    CALLS,
    CREATES,
    USES_TYPE,
    ANNOTATED_BY,
    OVERRIDES,
    ROUTES_TO
}
