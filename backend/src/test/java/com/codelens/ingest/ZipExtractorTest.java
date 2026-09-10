package com.codelens.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipExtractorTest {

    @TempDir
    Path dir;

    private final ZipExtractor zips = new ZipExtractor();

    @Test
    void extractsAndUnwrapsSingleTopFolder() throws IOException {
        var entries = new LinkedHashMap<String, String>();
        entries.put("repo-main/pom.xml", "<project/>");
        entries.put("repo-main/src/A.java", "class A {}");
        zips.extract(new ByteArrayInputStream(zip(entries)), dir.resolve("out"));

        assertThat(dir.resolve("out/repo-main/src/A.java")).exists();
        assertThat(Workspace.unwrap(dir.resolve("out"))).isEqualTo(dir.resolve("out/repo-main"));
    }

    @Test
    void rejectsZipSlip() throws IOException {
        byte[] evil = zip(Map.of("../evil.txt", "x"));
        assertThatThrownBy(() -> zips.extract(new ByteArrayInputStream(evil), dir.resolve("out")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(dir.resolve("evil.txt")).doesNotExist();
    }

    @Test
    void rejectsNonZip() {
        byte[] junk = "not a zip".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> zips.extract(new ByteArrayInputStream(junk), dir.resolve("out")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static byte[] zip(Map<String, String> entries) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ZipOutputStream(bytes)) {
            for (var e : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey()));
                out.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
