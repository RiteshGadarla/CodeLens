package com.codelens.parser;

import com.codelens.parser.model.AnalysisFacts;
import com.codelens.parser.model.EdgeFact;
import com.codelens.parser.model.EntityFact;
import com.codelens.parser.model.ParsedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

@Component
public class ProjectParser {

    private static final Logger log = LoggerFactory.getLogger(ProjectParser.class);

    private final JavaFileParser fileParser = new JavaFileParser();

    public AnalysisFacts analyze(Path root, Collection<String> paths, ExecutorService pool) {
        return resolve(parse(root, paths, pool), pool);
    }

    // pass 1, parallel
    public List<ParsedFile> parse(Path root, Collection<String> paths, ExecutorService pool) {
        var futures = paths.stream()
                .map(p -> CompletableFuture.supplyAsync(() -> parseOne(root, p), pool))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    public ParsedFile parseOne(Path root, String path) {
        try {
            return fileParser.parse(path, read(root.resolve(path)));
        } catch (IOException e) {
            return JavaFileParser.failed(path, 0, SourcePaths.isTest(path), e.toString());
        }
    }

    // pass 2, parallel per type
    public AnalysisFacts resolve(List<ParsedFile> files, ExecutorService pool) {
        return resolve(files, path -> true, pool);
    }

    // symbols from all files, facts only for types in matching files
    public AnalysisFacts resolve(List<ParsedFile> files, Predicate<String> extractFor, ExecutorService pool) {
        var table = new SymbolTable(files);
        table.duplicates().forEach(d -> log.warn("duplicate type skipped: {}", d));
        var extractor = new FactExtractor(table);

        var futures = table.types().stream()
                .filter(t -> extractFor.test(t.file().path()))
                .map(t -> CompletableFuture.supplyAsync(() -> extractor.extract(t), pool))
                .toList();

        var entities = new ArrayList<EntityFact>();
        var edges = new ArrayList<EdgeFact>();
        var seen = new HashSet<String>();
        for (var f : futures) {
            var r = f.join();
            for (EntityFact e : r.entities()) {
                if (seen.add(e.qualifiedName())) entities.add(e);
            }
            edges.addAll(r.edges());
        }
        return new AnalysisFacts(files, entities, edges);
    }

    private static String read(Path p) throws IOException {
        byte[] bytes = Files.readAllBytes(p);
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }
}
