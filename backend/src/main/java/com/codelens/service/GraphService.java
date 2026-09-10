package com.codelens.service;

import com.codelens.domain.AnalysisRun;
import com.codelens.domain.RunStatus;
import com.codelens.graph.DependencyGraph;
import com.codelens.graph.GraphRollup;
import com.codelens.repository.AnalysisRunRepository;
import com.codelens.repository.GraphStore;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

// in-memory graph per project, keyed by the last successful run
@Service
public class GraphService {

    private record Entry(long version, DependencyGraph graph, AtomicReference<DependencyGraph> types) {

        Entry(long version, DependencyGraph graph) {
            this(version, graph, new AtomicReference<>());
        }
    }

    private final GraphStore store;
    private final AnalysisRunRepository runs;
    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    public GraphService(GraphStore store, AnalysisRunRepository runs) {
        this.store = store;
        this.runs = runs;
    }

    public DependencyGraph graph(long projectId) {
        return entry(projectId).graph();
    }

    // type-level rollup, built lazily once per version
    public DependencyGraph types(long projectId) {
        Entry e = entry(projectId);
        return e.types().updateAndGet(t -> t != null ? t : GraphRollup.types(e.graph()));
    }

    public long version(long projectId) {
        return runs.findFirstByProjectIdAndStatusOrderByStartedAtDesc(projectId, RunStatus.SUCCESS)
                .map(AnalysisRun::getId)
                .orElse(0L);
    }

    public void put(long projectId, long version, DependencyGraph graph) {
        cache.put(projectId, new Entry(version, graph));
    }

    // run without changes: same graph, new version
    public void rekey(long projectId, long version) {
        cache.computeIfPresent(projectId, (k, e) -> new Entry(version, e.graph(), e.types()));
    }

    public void evict(long projectId) {
        cache.remove(projectId);
    }

    private Entry entry(long projectId) {
        long version = version(projectId);
        Entry e = cache.get(projectId);
        if (e != null && e.version() == version) return e;
        var fresh = new Entry(version, DependencyGraph.of(store.loadNodes(projectId), store.loadEdges(projectId)));
        cache.put(projectId, fresh);
        return fresh;
    }
}
