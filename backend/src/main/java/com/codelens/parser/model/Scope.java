package com.codelens.parser.model;

import java.io.Serializable;

// unresolved receiver of a call, resolved in pass 2
public sealed interface Scope extends Serializable {

    record None() implements Scope {}

    record This() implements Scope {}

    record Super() implements Scope {}

    record Name(String name) implements Scope {}

    record Field(Scope target, String name, String text) implements Scope {}

    record Call(CallSite call) implements Scope {}

    record Typed(String typeText) implements Scope {}

    record Unknown() implements Scope {}

    Scope NONE = new None();
    Scope THIS = new This();
    Scope SUPER = new Super();
    Scope UNKNOWN = new Unknown();
}
