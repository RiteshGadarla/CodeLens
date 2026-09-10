package com.codelens.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class Zips {

    private Zips() {
    }

    public static byte[] of(Path root, String prefix) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ZipOutputStream(bytes); var files = Files.walk(root)) {
            for (Path p : files.filter(Files::isRegularFile).toList()) {
                out.putNextEntry(new ZipEntry(prefix + root.relativize(p).toString().replace('\\', '/')));
                out.write(Files.readAllBytes(p));
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
