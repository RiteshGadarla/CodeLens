package com.codelens.ingest;

import com.codelens.parser.model.ParsedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

// pass-1 results per file, so incremental runs skip re-parsing unchanged files
@Component
public class ParseCache {

    public record Entry(String sha256, ParsedFile file) implements Serializable {
    }

    private static final Logger log = LoggerFactory.getLogger(ParseCache.class);
    private static final String FILE = "parsed.bin";
    private static final ObjectInputFilter FILTER =
            ObjectInputFilter.Config.createFilter("com.codelens.**;java.util.**;java.lang.**;!*");

    private final Workspace workspace;

    public ParseCache(Workspace workspace) {
        this.workspace = workspace;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Entry> load(long projectId) {
        Path file = workspace.cacheDir(projectId).resolve(FILE);
        if (!Files.isRegularFile(file)) return Map.of();
        try (var in = new ObjectInputStream(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(file))))) {
            in.setObjectInputFilter(FILTER);
            return (Map<String, Entry>) in.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            log.warn("ignoring unreadable parse cache for project {}: {}", projectId, e.toString());
            return Map.of();
        }
    }

    public void save(long projectId, Map<String, Entry> entries) {
        Path dir = workspace.cacheDir(projectId);
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(FILE + ".tmp");
            try (var out = new ObjectOutputStream(new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp))))) {
                out.writeObject(new HashMap<>(entries));
            }
            Files.move(tmp, dir.resolve(FILE), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("could not write parse cache for project {}: {}", projectId, e.toString());
        }
    }
}
