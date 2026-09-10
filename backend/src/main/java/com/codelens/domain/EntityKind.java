package com.codelens.domain;

public enum EntityKind {
    CLASS, INTERFACE, ENUM, RECORD, ANNOTATION,
    METHOD, CONSTRUCTOR, FIELD,
    ENDPOINT;

    public boolean isType() {
        return this == CLASS || this == INTERFACE || this == ENUM || this == RECORD || this == ANNOTATION;
    }

    public boolean isMember() {
        return this == METHOD || this == CONSTRUCTOR || this == FIELD;
    }
}
