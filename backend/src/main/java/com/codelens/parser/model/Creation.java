package com.codelens.parser.model;

import java.io.Serializable;

public record Creation(String typeText, int argCount, int line) implements Serializable {
}
