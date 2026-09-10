package com.codelens.impact;

import com.codelens.common.NotFoundException;
import com.codelens.domain.EntityKind;
import com.codelens.domain.Stereotype;
import com.codelens.graph.*;
import com.codelens.graph.DependencyGraph.Adj;
import com.codelens.graph.GraphTraversal.Direction;
import com.codelens.graph.GraphTraversal.Hop;
import com.codelens.metrics.RiskScorer;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ImpactAnalyzer {

    public ImpactResult analyze(DependencyGraph g, long entityId, ImpactOptions opts, RiskScorer.Scale scale) {
        int target = g.indexOf(entityId);
        if (target < 0) throw new NotFoundException("entity", entityId);
        var seeds = new LinkedHashSet<Integer>();
        collectSeeds(g, target, seeds);
        return run(g, target, seeds, g.node(target).complexity(), opts, scale);
    }

    public ImpactResult analyzeFile(DependencyGraph g, String filePath, ImpactOptions opts, RiskScorer.Scale scale) {
        var seeds = new LinkedHashSet<Integer>();
        int target = -1;
        int complexity = 0;
        for (int i = 0; i < g.size(); i++) {
            GraphNode n = g.node(i);
            if (!filePath.equals(n.filePath())) continue;
            seeds.add(i);
            if (n.kind().isType()) {
                complexity += n.complexity();
                if (target < 0 && g.parent(i) < 0) target = i;
            }
        }
        if (target < 0) throw new NotFoundException("file", filePath);
        return run(g, target, seeds, complexity, opts, scale);
    }

    // a type change covers all its members
    private static void collectSeeds(DependencyGraph g, int i, Set<Integer> out) {
        if (!out.add(i)) return;
        for (int m : g.members(i)) collectSeeds(g, m, out);
    }

    private ImpactResult run(DependencyGraph g, int target, Set<Integer> seeds, int complexity,
                             ImpactOptions opts, RiskScorer.Scale scale) {
        Map<Integer, Hop> hops = GraphTraversal.bfs(g, seeds, Direction.UPSTREAM, opts.maxDepth(), true);

        var affected = new ArrayList<ImpactResult.Affected>();
        var endpoints = new ArrayList<ImpactResult.Affected>();
        var tests = new LinkedHashMap<Integer, EntityRef>();
        var types = new LinkedHashMap<Integer, EntityRef>();
        var modules = new TreeSet<String>();
        var packages = new TreeSet<String>();
        int direct = 0;
        int maxDepth = 0;

        var ordered = hops.values().stream().sorted(Comparator.comparingInt(Hop::depth)).toList();
        for (Hop h : ordered) {
            int i = h.node();
            if (seeds.contains(i) || h.dispatch()) continue;
            if (g.isTest(i)) {
                int owner = g.ownerType(i);
                tests.putIfAbsent(owner, EntityRef.of(g, owner));
                if (!opts.includeTests()) continue;
            }
            GraphNode n = g.node(i);
            if (h.depth() == 1) direct++;
            maxDepth = Math.max(maxDepth, h.depth());
            if (n.module() != null) modules.add(n.module());
            if (n.packageName() != null && !n.packageName().isEmpty()) packages.add(n.packageName());
            int owner = g.ownerType(i);
            if (owner >= 0 && !seeds.contains(owner)) types.putIfAbsent(owner, EntityRef.of(g, owner));

            var a = new ImpactResult.Affected(EntityRef.of(g, i), h.depth(), h.edge(),
                    PathStep.of(g, GraphTraversal.pathTo(hops, i)));
            if (n.kind() == EntityKind.ENDPOINT) endpoints.add(a);
            affected.add(a);
        }

        var roles = new TreeMap<String, List<EntityRef>>();
        for (EntityRef t : types.values()) {
            roles.computeIfAbsent(t.role() == null ? "OTHER" : t.role().name(), k -> new ArrayList<>()).add(t);
        }

        boolean exposed = !endpoints.isEmpty() || seeds.stream().anyMatch(s -> isApi(g, s));
        var risk = RiskScorer.score(affected.size(), maxDepth, coupling(g, seeds), complexity, exposed, scale);

        boolean truncated = affected.size() > opts.maxResults();
        return new ImpactResult(EntityRef.of(g, target), seeds.size(), direct, affected.size(), maxDepth,
                List.copyOf(truncated ? affected.subList(0, opts.maxResults()) : affected),
                List.copyOf(endpoints), roles, List.copyOf(modules), List.copyOf(packages),
                List.copyOf(tests.values()), risk, truncated);
    }

    private static boolean isApi(DependencyGraph g, int i) {
        int owner = g.ownerType(i);
        return g.node(i).kind() == EntityKind.ENDPOINT
                || owner >= 0 && g.node(owner).stereotype() == Stereotype.CONTROLLER;
    }

    private static int coupling(DependencyGraph g, Set<Integer> seeds) {
        var neighbors = new HashSet<Integer>();
        for (int s : seeds) {
            for (Adj a : g.in(s)) if (!seeds.contains(a.node())) neighbors.add(a.node());
            for (Adj a : g.out(s)) if (!seeds.contains(a.node())) neighbors.add(a.node());
        }
        return neighbors.size();
    }
}
