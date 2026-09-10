package com.codelens.service;

import com.codelens.api.dto.*;
import com.codelens.config.CacheConfig;
import com.codelens.domain.*;
import com.codelens.graph.CycleDetector;
import com.codelens.graph.DependencyGraph;
import com.codelens.graph.EntityRef;
import com.codelens.graph.GraphNode;
import com.codelens.metrics.ModuleMetrics;
import com.codelens.metrics.RiskScorer;
import com.codelens.repository.EntityMetricRepository;
import com.codelens.repository.ModuleMetricRepository;
import com.codelens.repository.SourceFileRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MetricsQueryService {

    public enum Scope { ALL, TYPE, METHOD }

    private static final Set<String> SORTS = Set.of("riskScore", "complexity", "dependents", "dependencies", "fanIn",
            "fanOut", "depth");
    private static final Set<EntityKind> TYPES =
            EnumSet.of(EntityKind.CLASS, EntityKind.INTERFACE, EntityKind.ENUM, EntityKind.RECORD, EntityKind.ANNOTATION);
    private static final Set<EntityKind> METHODS = EnumSet.of(EntityKind.METHOD, EntityKind.CONSTRUCTOR);

    private final GraphService graphs;
    private final EntityMetricRepository metrics;
    private final ModuleMetricRepository modules;
    private final SourceFileRepository files;
    private final EntityQueryService entities;
    private final ProjectService projects;

    public MetricsQueryService(GraphService graphs, EntityMetricRepository metrics, ModuleMetricRepository modules,
                               SourceFileRepository files, EntityQueryService entities, ProjectService projects) {
        this.graphs = graphs;
        this.metrics = metrics;
        this.modules = modules;
        this.files = files;
        this.entities = entities;
        this.projects = projects;
    }

    @Cacheable(cacheNames = CacheConfig.OVERVIEW, key = "#projectId + ':' + @graphService.version(#projectId)")
    public OverviewDto overview(long projectId) {
        projects.get(projectId);
        DependencyGraph g = graphs.graph(projectId);

        var kinds = new TreeMap<String, Integer>();
        var roles = new TreeMap<String, Integer>();
        var moduleNames = new HashSet<String>();
        var packageNames = new HashSet<String>();
        int types = 0, methods = 0, endpoints = 0, tests = 0;
        for (GraphNode n : g.nodes()) {
            kinds.merge(n.kind().name(), 1, Integer::sum);
            if (n.module() != null) moduleNames.add(n.module());
            if (n.kind().isType()) {
                types++;
                roles.merge(n.stereotype() == null ? "OTHER" : n.stereotype().name(), 1, Integer::sum);
                if (n.stereotype() == Stereotype.TEST) tests++;
                if (n.packageName() != null && !n.packageName().isEmpty()) packageNames.add(n.packageName());
            } else if (METHODS.contains(n.kind())) {
                methods++;
            } else if (n.kind() == EntityKind.ENDPOINT) {
                endpoints++;
            }
        }

        int low = 0, medium = 0, high = 0;
        for (EntityMetric m : metrics.findByProjectId(projectId)) {
            var node = g.find(m.getEntityId());
            if (node.isEmpty() || !(node.get().kind().isType() || METHODS.contains(node.get().kind()))) continue;
            switch (RiskScorer.level(m.getRiskScore())) {
                case "HIGH" -> high++;
                case "MEDIUM" -> medium++;
                default -> low++;
            }
        }

        var fs = files.stats(projectId);
        DependencyGraph typeGraph = graphs.types(projectId);
        List<List<EntityRef>> cycles = CycleDetector.cycles(typeGraph).stream()
                .map(c -> Arrays.stream(c).mapToObj(i -> EntityRef.of(typeGraph, i)).toList())
                .toList();

        var stats = new OverviewDto.Stats(nz(fs.getFiles()), nz(fs.getLoc()), nz(fs.getParseErrors()), types, methods,
                endpoints, tests, g.edgeCount(), moduleNames.size(), packageNames.size());
        return new OverviewDto(stats, kinds, roles, new OverviewDto.RiskDistribution(low, medium, high),
                hotspots(projectId, Scope.ALL, "riskScore", 10).items(),
                modules(projectId, MetricLevel.MODULE).items(), cycles);
    }

    @Cacheable(cacheNames = CacheConfig.HOTSPOTS,
            key = "#projectId + ':' + @graphService.version(#projectId) + ':' + #scope + ':' + #sort + ':' + #limit")
    public HotspotList hotspots(long projectId, Scope scope, String sort, int limit) {
        projects.get(projectId);
        String property = SORTS.contains(sort) ? sort : "riskScore";
        Set<EntityKind> kinds = switch (scope) {
            case TYPE -> TYPES;
            case METHOD -> METHODS;
            case ALL -> {
                var all = EnumSet.copyOf(TYPES);
                all.addAll(METHODS);
                yield all;
            }
        };
        var page = PageRequest.of(0, Math.clamp(limit, 1, 200),
                Sort.by(Sort.Direction.DESC, property).and(Sort.by("entityId")));
        List<EntityMetric> rows = metrics.hotspots(projectId, kinds, page);
        Map<Long, EntitySummary> summaries = entities.summariesById(projectId, rows.stream().map(EntityMetric::getEntityId).toList());
        return new HotspotList(rows.stream()
                .filter(r -> summaries.containsKey(r.getEntityId()))
                .map(r -> new HotspotDto(summaries.get(r.getEntityId()), EntityMetricDto.of(r)))
                .toList());
    }

    @Cacheable(cacheNames = CacheConfig.MODULES, key = "#projectId + ':' + @graphService.version(#projectId) + ':' + #level")
    public ModuleMetricList modules(long projectId, MetricLevel level) {
        projects.get(projectId);
        return new ModuleMetricList(modules.findByProjectIdAndLevelOrderByInstabilityDesc(projectId, level).stream()
                .map(m -> new ModuleMetrics(m.getLevel(), m.getName(), m.getEntities(), m.getAfferent(), m.getEfferent(),
                        m.getInstability(), m.getAbstractness(), m.getDistance()))
                .toList());
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }
}
