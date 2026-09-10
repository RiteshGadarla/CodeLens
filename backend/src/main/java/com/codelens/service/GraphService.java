package com.codelens.service;

import com.codelens.domain.AnalysisRun;
import com.codelens.domain.RunStatus;
import com.codelens.graph.DependencyGraph;
import com.codelens.repository.AnalysisRunRepository;
import com.codelens.repository.GraphStore;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// in-memory graph per project, keyed by the last successful run
@Service
public class GraphService {

    private record Entry(long version, DependencyGraph graph) {
    }

    private final GraphStore store;
    private final AnalysisRunRepository runs;
    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    public GraphService(GraphStore store, AnalysisRunRepository runs) {
        this.store = store;
        this.runs = runs;
    }

    public DependencyGraph graph(long projectId) {
        long version = version(projectId);
        Entry e = cache.get(projectId);
        if (e != null && e.version() == version) return e.graph();
        var g = DependencyGraph.of(store.loadNodes(projectId), store.loadEdges(projectId));
        cache.put(projectId, new Entry(version, g));
        return g;
    }

    public long version(long projectId) {
        return runs.findFirstByProjectIdAndStatusOrderByStartedAtDesc(projectId, RunStatus.SUCCESS)
                .map(AnalysisRun::getId)
                .orElse(0L);
    }

    public void put(long projectId, long version, DependencyGraph graph) {
        cache.put(projectId, new Entry(version, graph));
    }

    public void evict(long projectId) {
        cache.remove(projectId);
    }
}
