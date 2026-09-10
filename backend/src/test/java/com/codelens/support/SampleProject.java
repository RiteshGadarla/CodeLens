package com.codelens.support;

import com.codelens.graph.DependencyGraph;
import com.codelens.graph.GraphFactory;
import com.codelens.parser.ProjectParser;
import com.codelens.parser.SourcePaths;
import com.codelens.parser.model.AnalysisFacts;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

// parsed once per JVM from src/test/resources/sample
public final class SampleProject {

    public static final String CTRL = "com.acme.api.UserController";
    public static final String SVC = "com.acme.service.UserService";
    public static final String IMPL = "com.acme.service.UserServiceImpl";
    public static final String REPO = "com.acme.repo.UserRepository";
    public static final String USER = "com.acme.domain.User";
    public static final String DTO = "com.acme.domain.UserDto";
    public static final String TEST = "com.acme.service.UserServiceImplTest";

    private static AnalysisFacts facts;
    private static DependencyGraph graph;

    private SampleProject() {
    }

    public static synchronized Path root() {
        try {
            return Path.of(SampleProject.class.getResource("/sample").toURI());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static synchronized AnalysisFacts facts() {
        if (facts == null) {
            Path root = root();
            var pool = Executors.newFixedThreadPool(4);
            try (var files = Files.walk(root)) {
                var paths = files.map(p -> root.relativize(p).toString().replace('\\', '/'))
                        .filter(SourcePaths::isJava)
                        .toList();
                facts = new ProjectParser().analyze(root, paths, pool);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                pool.shutdownNow();
            }
        }
        return facts;
    }

    public static synchronized DependencyGraph graph() {
        if (graph == null) graph = GraphFactory.fromFacts(facts(), p -> "root");
        return graph;
    }

    public static long id(String qualifiedName) {
        return graph().nodes().stream()
                .filter(n -> n.qualifiedName().equals(qualifiedName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing " + qualifiedName))
                .id();
    }
}
