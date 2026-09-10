package com.codelens.metrics;

import com.codelens.domain.EdgeType;
import com.codelens.domain.EntityKind;
import com.codelens.domain.MetricLevel;
import com.codelens.domain.Stereotype;
import com.codelens.graph.CycleDetector;
import com.codelens.graph.DependencyGraph;
import com.codelens.graph.DependencyGraph.Adj;
import com.codelens.graph.GraphNode;
import com.codelens.graph.GraphRollup;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

// members measured on the entity graph, types on the rolled-up type graph
@Component
public class MetricsCalculator {

    private record Raw(GraphNode node, int fanIn, int fanOut, int dependents, int dependencies, int depth,
                       boolean exposed) {
    }

    private static final class Stats {
        final int[] fanIn, fanOut, dependents, dependencies;
        final boolean[] exposed;
        int[] depth;

        Stats(int n) {
            fanIn = new int[n];
            fanOut = new int[n];
            dependents = new int[n];
            dependencies = new int[n];
            exposed = new boolean[n];
            depth = new int[n];
        }
    }

    public MetricsResult compute(DependencyGraph g, ExecutorService pool) {
        DependencyGraph types = GraphRollup.types(g);
        Stats ms = stats(g, pool);
        Stats ts = stats(types, pool);

        var raw = new ArrayList<Raw>();
        var exposedTypes = new HashSet<Long>();
        for (int i = 0; i < g.size(); i++) {
            GraphNode n = g.node(i);
            if (n.kind().isType()) continue;
            boolean exposed = ms.exposed[i] || n.kind() == EntityKind.ENDPOINT;
            raw.add(new Raw(n, ms.fanIn[i], ms.fanOut[i], ms.dependents[i], ms.dependencies[i], ms.depth[i], exposed));
            int owner = g.ownerType(i);
            if (exposed && owner >= 0) exposedTypes.add(g.node(owner).id());
        }
        for (int i = 0; i < types.size(); i++) {
            GraphNode n = types.node(i);
            boolean exposed = exposedTypes.contains(n.id()) || n.stereotype() == Stereotype.CONTROLLER;
            raw.add(new Raw(n, ts.fanIn[i], ts.fanOut[i], ts.dependents[i], ts.dependencies[i], ts.depth[i], exposed));
        }

        var scale = new RiskScorer.Scale(
                raw.stream().mapToInt(Raw::dependents).max().orElse(0),
                raw.stream().mapToInt(Raw::depth).max().orElse(0),
                raw.stream().mapToInt(r -> r.fanIn() + r.fanOut()).max().orElse(0),
                raw.stream().mapToInt(r -> r.node().complexity()).max().orElse(0));

        var nodes = raw.stream().map(r -> new NodeMetrics(r.node().id(), r.fanIn(), r.fanOut(), r.dependents(),
                r.dependencies(), r.depth(), r.node().complexity(), r.exposed(),
                RiskScorer.score(r.dependents(), r.depth(), r.fanIn() + r.fanOut(), r.node().complexity(),
                        r.exposed(), scale).score())).toList();

        var modules = new ArrayList<ModuleMetrics>();
        modules.addAll(coupling(types, MetricLevel.PACKAGE, GraphNode::packageName));
        modules.addAll(coupling(types, MetricLevel.MODULE, GraphNode::module));

        var cycles = CycleDetector.cycles(types).stream()
                .map(c -> Arrays.stream(c).mapToObj(i -> types.node(i).id()).toList())
                .toList();
        return new MetricsResult(nodes, modules, cycles, scale);
    }

    private Stats stats(DependencyGraph g, ExecutorService pool) {
        int n = g.size();
        var s = new Stats(n);
        if (n == 0) return s;
        for (int i = 0; i < n; i++) {
            s.fanIn[i] = distinct(g.in(i));
            s.fanOut[i] = distinct(g.out(i));
        }
        s.depth = CycleDetector.depth(g, CycleDetector.components(g));

        // one reach buffer per chunk
        int chunk = Math.max(64, n / (Runtime.getRuntime().availableProcessors() * 4));
        var futures = new ArrayList<CompletableFuture<Void>>();
        for (int start = 0; start < n; start += chunk) {
            int from = start, to = Math.min(n, start + chunk);
            futures.add(CompletableFuture.runAsync(() -> {
                var reach = new Reach(g);
                for (int i = from; i < to; i++) {
                    s.dependents[i] = reach.count(i, true);
                    s.exposed[i] = reach.hitEndpoint;
                    s.dependencies[i] = reach.count(i, false);
                }
            }, pool));
        }
        futures.forEach(CompletableFuture::join);
        return s;
    }

    private List<ModuleMetrics> coupling(DependencyGraph types, MetricLevel level, Function<GraphNode, String> key) {
        var groups = new TreeMap<String, List<Integer>>();
        for (int i = 0; i < types.size(); i++) {
            GraphNode n = types.node(i);
            if (n.stereotype() == Stereotype.TEST) continue;
            String k = key.apply(n);
            groups.computeIfAbsent(k == null || k.isEmpty() ? "(default)" : k, x -> new ArrayList<>()).add(i);
        }

        var out = new ArrayList<ModuleMetrics>();
        for (var e : groups.entrySet()) {
            var members = new HashSet<>(e.getValue());
            var efferent = new HashSet<Integer>();
            var afferent = new HashSet<Integer>();
            int abstracts = 0;
            for (int i : members) {
                if (types.node(i).abstractType()) abstracts++;
                for (Adj a : types.out(i)) {
                    if (!members.contains(a.node()) && !types.isTest(a.node())) efferent.add(a.node());
                }
                for (Adj a : types.in(i)) {
                    if (!members.contains(a.node()) && !types.isTest(a.node())) afferent.add(a.node());
                }
            }
            int ce = efferent.size(), ca = afferent.size();
            double instability = ca + ce == 0 ? 0 : (double) ce / (ca + ce);
            double abstractness = (double) abstracts / members.size();
            out.add(new ModuleMetrics(level, e.getKey(), members.size(), ca, ce, round(instability),
                    round(abstractness), round(Math.abs(abstractness + instability - 1))));
        }
        return out;
    }

    private static int distinct(Adj[] adj) {
        return (int) Arrays.stream(adj).mapToInt(Adj::node).distinct().count();
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    // reusable BFS buffers; stamps avoid clearing between runs
    private static final class Reach {
        private final DependencyGraph g;
        private final int[] stamp;
        private final int[] dispatched;
        private final int[] queue;
        private int gen;
        boolean hitEndpoint;

        Reach(DependencyGraph g) {
            this.g = g;
            stamp = new int[g.size()];
            dispatched = new int[g.size()];
            queue = new int[g.size()];
        }

        int count(int start, boolean upstream) {
            int token = ++gen;
            int head = 0, tail = 0;
            hitEndpoint = false;
            stamp[start] = token;
            queue[tail++] = start;
            while (head < tail) {
                int v = queue[head++];
                boolean viaDispatch = dispatched[v] == token;
                if (upstream) {
                    for (Adj a : g.out(v)) {
                        if (a.type() == EdgeType.OVERRIDES && stamp[a.node()] != token) {
                            stamp[a.node()] = token;
                            dispatched[a.node()] = token;
                            queue[tail++] = a.node();
                        }
                    }
                }
                for (Adj a : upstream ? g.in(v) : g.out(v)) {
                    if (viaDispatch && a.type() == EdgeType.OVERRIDES) continue;
                    int w = a.node();
                    if (stamp[w] != token) {
                        stamp[w] = token;
                        queue[tail++] = w;
                        if (g.node(w).kind() == EntityKind.ENDPOINT) hitEndpoint = true;
                    }
                }
            }
            return tail - 1;
        }
    }
}
