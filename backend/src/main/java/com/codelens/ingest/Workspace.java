package com.codelens.ingest;

import com.codelens.config.CodeLensProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// reposDir/<projectId>/{src,cache}
@Component
public class Workspace {

    private final Path reposDir;

    public Workspace(CodeLensProperties props) {
        this.reposDir = props.reposDir().toAbsolutePath().normalize();
    }

    public Path projectDir(long projectId) {
        return reposDir.resolve(String.valueOf(projectId));
    }

    public Path checkoutDir(long projectId) {
        return projectDir(projectId).resolve("src");
    }

    public Path cacheDir(long projectId) {
        return projectDir(projectId).resolve("cache");
    }

    public void deleteQuietly(long projectId) {
        try {
            FileSystemUtils.deleteRecursively(projectDir(projectId));
        } catch (IOException ignored) {
        }
    }

    // archives usually wrap everything in one top folder
    public static Path unwrap(Path dir) throws IOException {
        try (var children = Files.list(dir)) {
            var list = children.filter(p -> !p.getFileName().toString().startsWith(".")).limit(2).toList();
            return list.size() == 1 && Files.isDirectory(list.get(0)) ? list.get(0) : dir;
        }
    }
}
