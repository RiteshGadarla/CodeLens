package com.codelens.ai;

import com.codelens.domain.EntityKind;
import com.codelens.graph.GraphNode;
import com.codelens.parser.TypeNames;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

// retrieval chunks: one per method body, one outline per type
@Component
public class ChunkBuilder {

    private static final int MAX_LINES = 150;
    private static final int MAX_OUTLINE_MEMBERS = 60;

    private record Row(long id, String qualifiedName, String name, EntityKind kind, String signature,
                       String annotations, Integer start, Integer end, Long parentId, String path) {
    }

    private final JdbcTemplate jdbc;

    public ChunkBuilder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // path -> chunks; paths == null means the whole project
    public Map<String, List<AiClient.Chunk>> build(long projectId, Path root, Set<String> paths) {
        List<Row> rows = load(projectId, paths);
        Map<Long, List<Row>> children = rows.stream()
                .filter(r -> r.parentId() != null)
                .collect(Collectors.groupingBy(Row::parentId));
        Map<String, List<Row>> byPath = rows.stream()
                .collect(Collectors.groupingBy(Row::path, TreeMap::new, Collectors.toList()));

        var out = new LinkedHashMap<String, List<AiClient.Chunk>>();
        for (var entry : byPath.entrySet()) {
            List<String> lines = readLines(root.resolve(entry.getKey()));
            if (lines == null) continue;
            var chunks = new ArrayList<AiClient.Chunk>();
            for (Row r : entry.getValue()) {
                if (r.kind().isType()) chunks.add(outline(r, children.getOrDefault(r.id(), List.of())));
                else if (r.kind() == EntityKind.METHOD || r.kind() == EntityKind.CONSTRUCTOR) chunks.add(body(r, lines));
            }
            out.put(entry.getKey(), chunks);
        }
        return out;
    }

    // whole files per batch: the ai service replaces chunks path by path
    public static List<List<AiClient.Chunk>> batches(Collection<List<AiClient.Chunk>> files, int max) {
        var out = new ArrayList<List<AiClient.Chunk>>();
        var current = new ArrayList<AiClient.Chunk>();
        for (List<AiClient.Chunk> file : files) {
            if (file.isEmpty()) continue;
            if (!current.isEmpty() && current.size() + file.size() > max) {
                out.add(current);
                current = new ArrayList<>();
            }
            current.addAll(file);
        }
        if (!current.isEmpty()) out.add(current);
        return out;
    }

    private static AiClient.Chunk body(Row r, List<String> lines) {
        int start = Math.max(1, r.start() == null ? 1 : r.start());
        int end = Math.min(lines.size(), r.end() == null ? start : r.end());
        end = Math.min(end, start + MAX_LINES - 1);
        String code = start > end ? "" : String.join("\n", lines.subList(start - 1, end));
        return chunk(r, start, end, "// " + r.qualifiedName() + "\n" + code);
    }

    private static AiClient.Chunk outline(Row type, List<Row> members) {
        var sb = new StringBuilder("// ").append(type.qualifiedName()).append('\n');
        sb.append(annotations(type.annotations(), "\n"));
        sb.append(type.signature() == null ? type.name() : type.signature()).append(" {\n");
        members.stream()
                .filter(m -> m.kind() != EntityKind.ENDPOINT && m.signature() != null && !m.kind().isType())
                .limit(MAX_OUTLINE_MEMBERS)
                .forEach(m -> sb.append("    ").append(annotations(m.annotations(), " ")).append(m.signature())
                        .append(m.kind() == EntityKind.FIELD ? ";" : " { ... }").append('\n'));
        sb.append('}');
        int start = type.start() == null ? 1 : type.start();
        return chunk(type, start, type.end() == null ? start : type.end(), sb.toString());
    }

    private static AiClient.Chunk chunk(Row r, int start, int end, String text) {
        return new AiClient.Chunk(r.qualifiedName(), r.path(), r.kind().name(),
                GraphNode.labelOf(r.qualifiedName(), r.kind(), r.name()), r.qualifiedName(), start, end, text);
    }

    private static String annotations(String csv, String separator) {
        if (csv == null || csv.isBlank()) return "";
        return Arrays.stream(csv.split(",")).map(a -> "@" + TypeNames.simple(a) + separator).collect(Collectors.joining());
    }

    private List<Row> load(long projectId, Set<String> paths) {
        String sql = """
                select e.id, e.qualified_name, e.name, e.kind, e.signature, e.annotations, e.start_line, e.end_line,
                       e.parent_id, f.path
                from code_entity e join source_file f on f.id = e.file_id
                where e.project_id = ?""" + (paths == null ? "" : " and f.path = any(?)") + " order by f.path, e.start_line";
        Object[] args = paths == null ? new Object[]{projectId} : new Object[]{projectId, paths.toArray(String[]::new)};
        return jdbc.query(sql, (rs, i) -> new Row(rs.getLong("id"), rs.getString("qualified_name"), rs.getString("name"),
                EntityKind.valueOf(rs.getString("kind")), rs.getString("signature"), rs.getString("annotations"),
                rs.getObject("start_line", Integer.class), rs.getObject("end_line", Integer.class),
                rs.getObject("parent_id", Long.class), rs.getString("path")), args);
    }

    private static List<String> readLines(Path file) {
        try {
            try {
                return Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (MalformedInputException e) {
                return Files.readAllLines(file, StandardCharsets.ISO_8859_1);
            }
        } catch (IOException e) {
            return null;
        }
    }
}
