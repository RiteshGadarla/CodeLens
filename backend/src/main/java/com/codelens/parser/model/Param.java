package com.codelens.parser.model;

import java.io.Serializable;

public record Param(String name, String typeText, boolean varargs) implements Serializable {
}
