package com.codelens.ingest;

import com.codelens.config.CodeLensProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourceScannerTest {

    @TempDir
    Path root;

    @Test
    void scansJavaFilesWithModulesAndTestFlags() throws IOException {
        write("pom.xml", "<project/>");
        write("src/main/java/a/App.java", "class App {}");
        write("core/pom.xml", "<project/>");
        write("core/src/main/java/a/Core.java", "class Core {}");
        write("core/src/test/java/a/CoreTest.java", "class CoreTest {}");
        write("core/target/generated/Gen.java", "class Gen {}");
        write("web/src/main/java/a/Web.java", "class Web {}");
        write("src/main/java/a/package-info.java", "package a;");
        write("README.md", "# readme");

        var files = scanner(1_048_576).scan(root);

        assertThat(files).extracting(ScannedFile::path).containsExactly(
                "core/src/main/java/a/Core.java", "core/src/test/java/a/CoreTest.java",
                "src/main/java/a/App.java", "web/src/main/java/a/Web.java");
        assertThat(find(files, "core/src/main/java/a/Core.java").module()).isEqualTo("core");
        assertThat(find(files, "web/src/main/java/a/Web.java").module()).isEqualTo("root");
        assertThat(find(files, "core/src/test/java/a/CoreTest.java").test()).isTrue();
        assertThat(find(files, "src/main/java/a/App.java").sha256()).hasSize(64);
    }

    @Test
    void skipsOversizedFiles() throws IOException {
        write("A.java", "class A { /* a fairly long comment */ }");
        write("B.java", "class B{}");
        assertThat(scanner(12).scan(root)).extracting(ScannedFile::path).containsExactly("B.java");
    }

    @Test
    void hashChangesWithContent() throws IOException {
        write("A.java", "class A {}");
        String before = SourceScanner.sha256(root.resolve("A.java"));
        write("A.java", "class A { int x; }");
        assertThat(SourceScanner.sha256(root.resolve("A.java"))).isNotEqualTo(before);
    }

    private SourceScanner scanner(long maxBytes) {
        return new SourceScanner(new CodeLensProperties(root, new CodeLensProperties.Ai("x", Duration.ofSeconds(1), false),
                new CodeLensProperties.Analysis(2, maxBytes), new CodeLensProperties.Cors(List.of())));
    }

    private void write(String rel, String content) throws IOException {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    private static ScannedFile find(List<ScannedFile> files, String path) {
        return files.stream().filter(f -> f.path().equals(path)).findFirst().orElseThrow();
    }
}
