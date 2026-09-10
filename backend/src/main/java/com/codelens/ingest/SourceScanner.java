package com.codelens.ingest;

import com.codelens.config.CodeLensProperties;
import com.codelens.parser.SourcePaths;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

// finds java sources, their build module and content hash
@Component
public class SourceScanner {

    private static final Set<String> SKIP_DIRS = Set.of(".git", ".svn", ".hg", ".idea", ".vscode", ".gradle",
            ".mvn", "target", "build", "out", "bin", "node_modules", "dist");
    private static final List<String> BUILD_FILES = List.of("pom.xml", "build.gradle", "build.gradle.kts");
    public static final String ROOT_MODULE = "root";

    private final long maxFileBytes;

    public SourceScanner(CodeLensProperties props) {
        this.maxFileBytes = props.analysis().maxFileBytes();
    }

    public List<ScannedFile> scan(Path root) throws IOException {
        var files = new ArrayList<ScannedFile>();
        var modules = new HashMap<Path, String>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                boolean skip = !dir.equals(root) && SKIP_DIRS.contains(dir.getFileName().toString());
                return skip ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String rel = relative(root, file);
                if (attrs.isRegularFile() && SourcePaths.isJava(rel) && attrs.size() <= maxFileBytes) {
                    files.add(new ScannedFile(rel, module(root, file.getParent(), modules), sha256(file),
                            attrs.size(), SourcePaths.isTest(rel)));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Comparator.comparing(ScannedFile::path));
        return files;
    }

    public static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // nearest ancestor with a build file
    private static String module(Path root, Path dir, Map<Path, String> cache) {
        var visited = new ArrayList<Path>();
        String found = null;
        for (Path d = dir; d != null && d.startsWith(root); d = d.getParent()) {
            String cached = cache.get(d);
            if (cached != null) {
                found = cached;
                break;
            }
            visited.add(d);
            if (hasBuildFile(d)) {
                found = d.equals(root) ? ROOT_MODULE : relative(root, d);
                break;
            }
        }
        String module = found == null ? ROOT_MODULE : found;
        visited.forEach(v -> cache.put(v, module));
        return module;
    }

    private static boolean hasBuildFile(Path dir) {
        return BUILD_FILES.stream().anyMatch(b -> Files.isRegularFile(dir.resolve(b)));
    }

    private static String relative(Path root, Path p) {
        return root.relativize(p).toString().replace('\\', '/');
    }
}
