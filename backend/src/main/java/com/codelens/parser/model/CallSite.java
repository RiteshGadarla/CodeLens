package com.codelens.parser.model;

import java.io.Serializable;

// argCount -1 = method reference (any arity)
public record CallSite(String name, int argCount, Scope scope, int line) implements Serializable {
}
