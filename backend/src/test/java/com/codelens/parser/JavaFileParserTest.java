package com.codelens.parser;

import com.codelens.domain.EntityKind;
import com.codelens.domain.Stereotype;
import com.codelens.parser.model.MethodDecl;
import com.codelens.parser.model.Scope;
import com.codelens.parser.model.TypeDecl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JavaFileParserTest {

    private final JavaFileParser parser = new JavaFileParser();

    @Test
    void countsCyclomaticComplexity() {
        var f = parser.parse("src/main/java/a/A.java", """
                package a;
                class A {
                    int m(int x) {
                        switch (x) {
                            case 1, 2 -> { return 1; }
                            default -> { return x > 0 ? 2 : 3; }
                        }
                    }
                    void n() {
                        try { run(); } catch (RuntimeException e) { }
                        while (true && false) { }
                    }
                    void run() { }
                }
                """);
        var methods = f.types().get(0).methods();
        assertThat(methods).extracting(MethodDecl::complexity).containsExactly(4, 4, 1);
    }

    @Test
    void extractsImportsNestedTypesAndJaxRsEndpoints() {
        var f = parser.parse("src/main/java/a/Api.java", """
                package a;
                import static a.Util.helper;
                import b.*;
                import javax.ws.rs.*;
                @Path("/v1")
                public class Api {
                    @GET @Path("items/{id}")
                    public String item(String id) { return helper(id); }
                    static class Inner { }
                    interface Port { void send(); }
                }
                """);
        assertThat(f.packageName()).isEqualTo("a");
        assertThat(f.staticImports()).containsExactly("a.Util.helper");
        assertThat(f.wildcardImports()).containsExactly("b", "javax.ws.rs");
        assertThat(f.types()).extracting(TypeDecl::qualifiedName).containsExactly("a.Api", "a.Api.Inner", "a.Api.Port");
        assertThat(f.types().get(2).kind()).isEqualTo(EntityKind.INTERFACE);

        var api = f.types().get(0);
        assertThat(api.stereotype()).isEqualTo(Stereotype.CONTROLLER);
        assertThat(api.methods().get(0).httpMethod()).isEqualTo("GET");
        assertThat(api.methods().get(0).httpPath()).isEqualTo("/v1/items/{id}");
    }

    @Test
    void keepsVarInitializerAsScope() {
        var f = parser.parse("src/main/java/a/A.java", """
                package a;
                class A {
                    void m() {
                        var b = new B();
                        var c = b.make();
                        String s = "x";
                        c.go();
                    }
                }
                """);
        var locals = f.types().get(0).methods().get(0).locals();
        assertThat(locals.get("b")).isEqualTo(new Scope.Typed("B"));
        assertThat(locals.get("c")).isInstanceOf(Scope.Call.class);
        assertThat(locals.get("s")).isEqualTo(new Scope.Typed("String"));
    }

    @Test
    void addsRecordAccessors() {
        var f = parser.parse("src/main/java/a/P.java", "package a; record P(String name, int age) { }");
        var p = f.types().get(0);
        assertThat(p.kind()).isEqualTo(EntityKind.RECORD);
        assertThat(p.methods()).extracting(MethodDecl::name).containsExactly("name", "age");
    }

    @Test
    void recordsParseErrors() {
        var f = parser.parse("src/main/java/a/Bad.java", "class Bad { void x( }");
        assertThat(f.parseError()).isNotBlank();
    }
}
