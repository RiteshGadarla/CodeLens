package com.codelens.ingest;

import com.codelens.config.CodeLensProperties;
import com.codelens.parser.JavaFileParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ParseCacheTest {

    @TempDir
    Path dir;

    @Test
    void roundTripsParsedFiles() {
        var cache = new ParseCache(workspace());
        var parsed = new JavaFileParser().parse("src/main/java/a/A.java", """
                package a;
                import java.util.List;
                public class A {
                    private final B b = new B();
                    List<String> names(int n) { var x = b.load(n); return x.names(); }
                }
                """);

        cache.save(7, Map.of(parsed.path(), new ParseCache.Entry("sha", parsed)));
        var loaded = cache.load(7);

        assertThat(loaded).containsOnlyKeys("src/main/java/a/A.java");
        assertThat(loaded.get(parsed.path()).file()).isEqualTo(parsed);
    }

    @Test
    void corruptOrMissingCacheIsEmpty() throws IOException {
        var ws = workspace();
        var cache = new ParseCache(ws);
        assertThat(cache.load(1)).isEmpty();

        Files.createDirectories(ws.cacheDir(1));
        Files.writeString(ws.cacheDir(1).resolve("parsed.bin"), "garbage");
        assertThat(cache.load(1)).isEmpty();
    }

    private Workspace workspace() {
        return new Workspace(new CodeLensProperties(dir, new CodeLensProperties.Ai("x", Duration.ofSeconds(1), false),
                new CodeLensProperties.Analysis(2, 1_000_000), new CodeLensProperties.Cors(List.of())));
    }
}
