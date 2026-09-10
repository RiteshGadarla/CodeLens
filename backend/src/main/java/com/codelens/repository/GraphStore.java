package com.codelens.repository;

import com.codelens.domain.EdgeType;
import com.codelens.domain.EntityKind;
import com.codelens.domain.Stereotype;
import com.codelens.graph.GraphEdge;
import com.codelens.graph.GraphFactory;
import com.codelens.graph.GraphNode;
import com.codelens.metrics.MetricsResult;
import com.codelens.metrics.ModuleMetrics;
import com.codelens.metrics.NodeMetrics;
import com.codelens.parser.model.EdgeFact;
import com.codelens.parser.model.EntityFact;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

// bulk jdbc for the analysis write path and graph loading
@Repository
public class GraphStore {

    private static final int BATCH = 1000;

    private final JdbcTemplate jdbc;

    public GraphStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void deleteProjectData(long projectId) {
        jdbc.update("delete from dependency_edge where project_id = ?", projectId);
        jdbc.update("delete from entity_metric where project_id = ?", projectId);
        jdbc.update("delete from module_metric where project_id = ?", projectId);
        jdbc.update("delete from code_entity where project_id = ?", projectId);
        jdbc.update("delete from source_file where project_id = ?", projectId);
    }

    public Map<String, Long> insertFiles(long projectId, Collection<FileRow> files) {
        long[] ids = nextIds("source_file_seq", files.size());
        var map = new HashMap<String, Long>();
        var args = new ArrayList<Object[]>(files.size());
        int i = 0;
        for (FileRow f : files) {
            long id = ids[i++];
            map.put(f.path(), id);
            args.add(new Object[]{id, projectId, f.path(), f.module(), f.packageName(), f.sha256(), f.loc(),
                    f.test(), f.parseError()});
        }
        batch("""
                insert into source_file (id, project_id, path, module, package_name, sha256, loc, test, parse_error)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)""", args);
        return map;
    }

    // known = ids of entities already stored (parents outside this batch)
    public Map<String, Long> insertEntities(long projectId, List<EntityFact> entities, Map<String, Long> fileIds,
                                            Map<String, Long> known) {
        var ordered = parentsFirst(entities);
        long[] ids = nextIds("code_entity_seq", ordered.size());
        var map = new HashMap<>(known);
        for (int i = 0; i < ordered.size(); i++) map.put(ordered.get(i).qualifiedName(), ids[i]);

        var args = new ArrayList<Object[]>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            EntityFact e = ordered.get(i);
            Long parent = e.parentQualifiedName() == null ? null : map.get(e.parentQualifiedName());
            args.add(new Object[]{ids[i], projectId, fileIds.get(e.filePath()), parent, e.kind().name(),
                    cut(e.name(), 500), e.qualifiedName(), cut(e.packageName(), 500), e.signature(),
                    e.visibility(), cut(e.modifiers(), 200), e.annotations(),
                    e.stereotype() == null ? null : e.stereotype().name(), cut(e.httpMethod(), 10), e.httpPath(),
                    e.startLine(), e.endLine(), e.complexity()});
        }
        batch("""
                insert into code_entity (id, project_id, file_id, parent_id, kind, name, qualified_name, package_name,
                    signature, visibility, modifiers, annotations, stereotype, http_method, http_path,
                    start_line, end_line, complexity)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", args);
        return map;
    }

    public int insertEdges(long projectId, List<EdgeFact> edges, Map<String, Long> entityIds,
                           Map<String, Long> fileIds) {
        var args = new ArrayList<Object[]>(edges.size());
        for (EdgeFact e : edges) {
            Long s = entityIds.get(e.sourceQn());
            Long t = entityIds.get(e.targetQn());
            Long f = fileIds.get(e.filePath());
            if (s == null || t == null || f == null) continue;
            args.add(new Object[]{projectId, s, t, e.type().name(), e.weight(), f});
        }
        batch("""
                insert into dependency_edge (project_id, source_id, target_id, type, weight, file_id)
                values (?, ?, ?, ?, ?, ?)
                on conflict (source_id, target_id, type) do update set weight = dependency_edge.weight + excluded.weight""",
                args);
        return args.size();
    }

    public void replaceMetrics(long projectId, MetricsResult m) {
        jdbc.update("delete from entity_metric where project_id = ?", projectId);
        jdbc.update("delete from module_metric where project_id = ?", projectId);

        var nodes = new ArrayList<Object[]>(m.nodes().size());
        for (NodeMetrics n : m.nodes()) {
            nodes.add(new Object[]{n.entityId(), projectId, n.fanIn(), n.fanOut(), n.dependents(), n.dependencies(),
                    n.depth(), n.complexity(), n.riskScore(), n.exposed()});
        }
        batch("""
                insert into entity_metric (entity_id, project_id, fan_in, fan_out, dependents, dependencies, depth,
                    complexity, risk_score, exposed)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", nodes);

        var modules = new ArrayList<Object[]>(m.modules().size());
        for (ModuleMetrics mm : m.modules()) {
            modules.add(new Object[]{projectId, mm.level().name(), cut(mm.name(), 500), mm.entities(), mm.afferent(),
                    mm.efferent(), mm.instability(), mm.abstractness(), mm.distance()});
        }
        batch("""
                insert into module_metric (project_id, level, name, entities, afferent, efferent, instability,
                    abstractness, distance)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)""", modules);
    }

    public List<GraphNode> loadNodes(long projectId) {
        return jdbc.query("""
                select e.id, e.qualified_name, e.name, e.kind, e.parent_id, e.file_id, f.path, f.module,
                       e.package_name, e.stereotype, e.http_method, e.http_path, e.complexity, e.modifiers
                from code_entity e
                join source_file f on f.id = e.file_id
                where e.project_id = ?
                order by e.id""", (rs, i) -> {
            var kind = EntityKind.valueOf(rs.getString("kind"));
            String stereotype = rs.getString("stereotype");
            return new GraphNode(rs.getLong("id"), rs.getString("qualified_name"), rs.getString("name"), kind,
                    rs.getObject("parent_id", Long.class), rs.getLong("file_id"), rs.getString("path"),
                    rs.getString("module"), rs.getString("package_name"),
                    stereotype == null ? null : Stereotype.valueOf(stereotype), rs.getString("http_method"),
                    rs.getString("http_path"), rs.getInt("complexity"),
                    GraphFactory.isAbstract(kind, rs.getString("modifiers")));
        }, projectId);
    }

    public List<GraphEdge> loadEdges(long projectId) {
        return jdbc.query("select source_id, target_id, type, weight from dependency_edge where project_id = ?",
                (rs, i) -> new GraphEdge(rs.getLong(1), rs.getLong(2), EdgeType.valueOf(rs.getString(3)), rs.getInt(4)),
                projectId);
    }

    private long[] nextIds(String sequence, int n) {
        if (n == 0) return new long[0];
        return jdbc.queryForList("select nextval('" + sequence + "') from generate_series(1, ?)", Long.class, n)
                .stream().mapToLong(Long::longValue).toArray();
    }

    private void batch(String sql, List<Object[]> args) {
        for (int i = 0; i < args.size(); i += BATCH) {
            jdbc.batchUpdate(sql, args.subList(i, Math.min(args.size(), i + BATCH)));
        }
    }

    // FK on parent_id needs parents inserted first
    private static List<EntityFact> parentsFirst(List<EntityFact> entities) {
        var byQn = new HashMap<String, EntityFact>();
        entities.forEach(e -> byQn.put(e.qualifiedName(), e));
        return entities.stream().sorted(Comparator.comparingInt(e -> depth(e, byQn))).toList();
    }

    private static int depth(EntityFact e, Map<String, EntityFact> byQn) {
        int d = 0;
        for (String p = e.parentQualifiedName(); p != null && d < 64; d++) {
            EntityFact parent = byQn.get(p);
            p = parent == null ? null : parent.parentQualifiedName();
        }
        return d;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
