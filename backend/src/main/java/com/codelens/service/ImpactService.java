package com.codelens.service;

import com.codelens.config.CacheConfig;
import com.codelens.impact.ImpactAnalyzer;
import com.codelens.impact.ImpactOptions;
import com.codelens.impact.ImpactResult;
import com.codelens.metrics.RiskScorer;
import com.codelens.repository.EntityMetricRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class ImpactService {

    private static final int MAX_RESULTS = 500;

    private final ImpactAnalyzer analyzer;
    private final GraphService graphs;
    private final EntityMetricRepository metrics;
    private final ProjectService projects;

    public ImpactService(ImpactAnalyzer analyzer, GraphService graphs, EntityMetricRepository metrics,
                         ProjectService projects) {
        this.analyzer = analyzer;
        this.graphs = graphs;
        this.metrics = metrics;
        this.projects = projects;
    }

    @Cacheable(cacheNames = CacheConfig.IMPACT,
            key = "#projectId + ':' + @graphService.version(#projectId) + ':e' + #entityId + ':' + #depth + ':' + #includeTests")
    public ImpactResult forEntity(long projectId, long entityId, int depth, boolean includeTests) {
        projects.get(projectId);
        return analyzer.analyze(graphs.graph(projectId), entityId, options(depth, includeTests), scale(projectId));
    }

    @Cacheable(cacheNames = CacheConfig.IMPACT,
            key = "#projectId + ':' + @graphService.version(#projectId) + ':f' + #path + ':' + #depth + ':' + #includeTests")
    public ImpactResult forFile(long projectId, String path, int depth, boolean includeTests) {
        projects.get(projectId);
        return analyzer.analyzeFile(graphs.graph(projectId), path, options(depth, includeTests), scale(projectId));
    }

    // project maxima used to normalise risk factors
    public RiskScorer.Scale scale(long projectId) {
        var s = metrics.scale(projectId);
        if (s == null) return new RiskScorer.Scale(0, 0, 0, 0);
        return new RiskScorer.Scale(nz(s.getDependents()), nz(s.getDepth()), nz(s.getCoupling()), nz(s.getComplexity()));
    }

    private static ImpactOptions options(int depth, boolean includeTests) {
        return new ImpactOptions(Math.clamp(depth, 1, 30), includeTests, MAX_RESULTS);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
