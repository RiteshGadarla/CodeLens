package com.codelens.ingest;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class ZipExtractor {

    private static final long MAX_BYTES = 500L * 1024 * 1024;
    private static final int MAX_ENTRIES = 100_000;

    public int extract(InputStream in, Path target) throws IOException {
        Files.createDirectories(target);
        Path root = target.toRealPath();
        int entries = 0;
        long total = 0;
        byte[] buf = new byte[8192];
        try (var zip = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new IllegalArgumentException("archive has too many entries");
                Path out = root.resolve(e.getName()).normalize();
                // zip slip
                if (!out.startsWith(root)) throw new IllegalArgumentException("illegal path in archive: " + e.getName());
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                try (var os = Files.newOutputStream(out)) {
                    int n;
                    while ((n = zip.read(buf)) > 0) {
                        total += n;
                        if (total > MAX_BYTES) throw new IllegalArgumentException("archive is too large");
                        os.write(buf, 0, n);
                    }
                }
            }
        }
        if (entries == 0) throw new IllegalArgumentException("archive is empty or not a zip");
        return entries;
    }
}
