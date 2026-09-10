package com.codelens.service;

import com.codelens.api.dto.GraphView;
import com.codelens.api.dto.PathResult;
import com.codelens.common.NotFoundException;
import com.codelens.config.CacheConfig;
import com.codelens.domain.EdgeType;
import com.codelens.domain.EntityKind;
import com.codelens.domain.EntityMetric;
import com.codelens.graph.*;
import com.codelens.graph.DependencyGraph.Adj;
import com.codelens.graph.GraphTraversal.Direction;
import com.codelens.repository.EntityMetricRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GraphQueryService {

    public enum Level { TYPE, MEMBER }

    public enum Expand { BOTH, UPSTREAM, DOWNSTREAM }

    private static final Set<EntityKind> TYPE_KINDS =
            EnumSet.of(EntityKind.CLASS, EntityKind.INTERFACE, EntityKind.ENUM, EntityKind.RECORD, EntityKind.ANNOTATION);
    private static final Set<EntityKind> MEMBER_KINDS =
            EnumSet.of(EntityKind.METHOD, EntityKind.CONSTRUCTOR, EntityKind.ENDPOINT);
    private static final int MAX_PATHS = 20;

    private final GraphService graphs;
    private final EntityMetricRepository metrics;
    private final ProjectService projects;

    public GraphQueryService(GraphService graphs, EntityMetricRepository metrics, ProjectService projects) {
        this.graphs = graphs;
        this.metrics = metrics;
        this.projects = projects;
    }

    @Cacheable(cacheNames = CacheConfig.GRAPH, key = "#projectId + ':' + @graphService.version(#projectId) + ':' + #level"
            + " + ':' + #focusId + ':' + #depth + ':' + #expand + ':' + #limit + ':' + #includeTests")
    public GraphView view(long projectId, Level level, Long focusId, int depth, Expand expand, int limit,
                          boolean includeTests) {
        projects.get(projectId);
        DependencyGraph g = level == Level.TYPE ? graphs.types(projectId) : graphs.graph(projectId);
        int max = Math.clamp(limit, 1, 500);

        // candidate node -> distance from focus
        var distance = new LinkedHashMap<Integer, Integer>();
        int focus = -1;
        if (focusId != null) {
            focus = resolveFocus(projectId, g, level, focusId);
            int hops = Math.clamp(depth, 1, 6);
            if (expand != Expand.DOWNSTREAM) merge(distance, GraphTraversal.bfs(g, List.of(focus), Direction.UPSTREAM, hops, false));
            if (expand != Expand.UPSTREAM) merge(distance, GraphTraversal.bfs(g, List.of(focus), Direction.DOWNSTREAM, hops, false));
        } else {
            var page = PageRequest.of(0, max * 2, Sort.by(Sort.Direction.DESC, "riskScore").and(Sort.by("entityId")));
            for (EntityMetric m : metrics.hotspots(projectId, level == Level.TYPE ? TYPE_KINDS : MEMBER_KINDS, page)) {
                int i = g.indexOf(m.getEntityId());
                if (i >= 0) distance.put(i, -1);
            }
        }

        int focusIndex = focus;
        var candidates = distance.keySet().stream()
                .filter(i -> includeTests || i == focusIndex || !g.isTest(i))
                .toList();
        Map<Long, EntityMetric> m = metrics.findAllById(candidates.stream().map(i -> g.node(i).id()).toList()).stream()
                .collect(Collectors.toMap(EntityMetric::getEntityId, Function.identity()));

        var selected = candidates.stream()
                .sorted(Comparator.comparingInt((Integer i) -> distance.get(i) < 0 ? 0 : distance.get(i))
                        .thenComparing(i -> -risk(m, g.node(i).id())))
                .limit(max)
                .toList();
        var chosen = new HashSet<>(selected);

        var nodes = selected.stream().map(i -> {
            GraphNode n = g.node(i);
            EntityMetric em = m.get(n.id());
            return new GraphView.Node(n.id(), n.label(), n.qualifiedName(), n.kind(), EntityRef.of(g, i).role(),
                    n.module(), n.packageName(), n.complexity(), em == null ? 0 : em.getRiskScore(),
                    em == null ? 0 : em.getFanIn(), em == null ? 0 : em.getFanOut(), distance.get(i), i == focusIndex);
        }).toList();

        var edges = new ArrayList<GraphView.Edge>();
        for (int s : selected) {
            for (Adj a : g.out(s)) {
                if (chosen.contains(a.node())) {
                    edges.add(new GraphView.Edge(g.node(s).id(), g.node(a.node()).id(), a.type(), a.weight()));
                }
            }
        }
        return new GraphView(level.name(), focusId, nodes, edges, candidates.size() > max);
    }

    public PathResult path(long projectId, long fromId, long toId, int maxDepth, boolean all) {
        projects.get(projectId);
        DependencyGraph full = graphs.graph(projectId);
        int f = full.indexOf(fromId);
        int t = full.indexOf(toId);
        if (f < 0) throw new NotFoundException("entity", fromId);
        if (t < 0) throw new NotFoundException("entity", toId);

        // two types: use rolled-up edges
        DependencyGraph g = full.node(f).kind().isType() && full.node(t).kind().isType() ? graphs.types(projectId) : full;
        int from = g.indexOf(fromId);
        int to = g.indexOf(toId);
        int hops = Math.clamp(maxDepth, 1, 30);

        for (Direction dir : List.of(Direction.DOWNSTREAM, Direction.UPSTREAM)) {
            List<List<PathStep>> paths = all
                    ? GraphTraversal.allPaths(g, from, to, dir, hops, MAX_PATHS).stream().map(p -> steps(g, p, dir)).toList()
                    : GraphTraversal.shortestPath(g, from, to, dir, hops).map(h -> List.of(PathStep.of(g, h))).orElse(List.of());
            if (!paths.isEmpty()) return new PathResult(true, dir, paths);
        }
        return new PathResult(false, null, List.of());
    }

    public List<List<EntityRef>> cycles(long projectId) {
        projects.get(projectId);
        DependencyGraph types = graphs.types(projectId);
        return CycleDetector.cycles(types).stream()
                .map(c -> Arrays.stream(c).mapToObj(i -> EntityRef.of(types, i)).toList())
                .toList();
    }

    private int resolveFocus(long projectId, DependencyGraph g, Level level, long focusId) {
        int i = g.indexOf(focusId);
        if (i >= 0) return i;
        if (level == Level.TYPE) {
            DependencyGraph full = graphs.graph(projectId);
            int fi = full.indexOf(focusId);
            int owner = fi < 0 ? -1 : full.ownerType(fi);
            if (owner >= 0) return g.indexOf(full.node(owner).id());
        }
        throw new NotFoundException("entity", focusId);
    }

    private static void merge(Map<Integer, Integer> distance, Map<Integer, GraphTraversal.Hop> hops) {
        hops.values().forEach(h -> distance.merge(h.node(), h.depth(), Math::min));
    }

    private static double risk(Map<Long, EntityMetric> m, long id) {
        EntityMetric em = m.get(id);
        return em == null ? 0 : em.getRiskScore();
    }

    private static List<PathStep> steps(DependencyGraph g, List<Integer> path, Direction dir) {
        var steps = new ArrayList<PathStep>();
        for (int k = 0; k < path.size(); k++) {
            GraphNode n = g.node(path.get(k));
            EdgeType edge = null;
            if (k > 0) {
                int prev = path.get(k - 1);
                int cur = path.get(k);
                edge = Arrays.stream(dir == Direction.DOWNSTREAM ? g.out(prev) : g.in(prev))
                        .filter(a -> a.node() == cur).map(Adj::type).findFirst().orElse(null);
            }
            steps.add(new PathStep(n.id(), n.label(), n.kind(), edge, false));
        }
        return steps;
    }
}
