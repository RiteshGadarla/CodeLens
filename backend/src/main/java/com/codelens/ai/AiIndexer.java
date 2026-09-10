package com.codelens.ai;

import com.codelens.common.NotFoundException;
import com.codelens.domain.Project;
import com.codelens.repository.ProjectRepository;
import com.codelens.service.AnalysisCompletedEvent;
import com.codelens.service.ProjectDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

// keeps the rag index in sync with analysis runs
@Component
public class AiIndexer {

    private static final Logger log = LoggerFactory.getLogger(AiIndexer.class);
    private static final int BATCH = 300;

    private final AiClient ai;
    private final ChunkBuilder chunks;
    private final ProjectRepository projects;
    private final ExecutorService jobExecutor;
    private final Map<Long, Object> locks = new ConcurrentHashMap<>();

    public AiIndexer(AiClient ai, ChunkBuilder chunks, ProjectRepository projects,
                     @Qualifier("jobExecutor") ExecutorService jobExecutor) {
        this.ai = ai;
        this.chunks = chunks;
        this.projects = projects;
        this.jobExecutor = jobExecutor;
    }

    @EventListener
    public void onAnalysis(AnalysisCompletedEvent e) {
        if (!ai.enabled()) return;
        jobExecutor.submit(() -> {
            try {
                index(e.projectId(), e.reset() ? null : e.changedPaths(), e.deletedPaths());
            } catch (RuntimeException ex) {
                log.warn("indexing project {} skipped: {}", e.projectId(), ex.getMessage());
            }
        });
    }

    @EventListener
    public void onDelete(ProjectDeletedEvent e) {
        if (!ai.enabled()) return;
        jobExecutor.submit(() -> {
            try {
                ai.deleteIndex(e.projectId());
            } catch (RuntimeException ex) {
                log.warn("could not drop index for project {}: {}", e.projectId(), ex.getMessage());
            }
        });
    }

    // paths == null rebuilds the index; returns chunks sent
    public int index(long projectId, Set<String> paths, Set<String> removed) {
        synchronized (locks.computeIfAbsent(projectId, k -> new Object())) {
            Project p = projects.findById(projectId).orElseThrow(() -> new NotFoundException("project", projectId));
            var byFile = chunks.build(projectId, Path.of(p.getLocalPath()), paths);

            var removedPaths = new TreeSet<>(removed);
            if (paths != null) {
                paths.stream().filter(path -> byFile.getOrDefault(path, List.of()).isEmpty()).forEach(removedPaths::add);
            }
            boolean reset = paths == null;
            var batches = ChunkBuilder.batches(byFile.values(), BATCH);
            if (batches.isEmpty()) {
                if (reset || !removedPaths.isEmpty()) {
                    ai.index(projectId, new AiClient.IndexRequest(List.of(), List.copyOf(removedPaths), reset));
                }
                return 0;
            }

            int sent = 0;
            AiClient.IndexResult last = null;
            for (int i = 0; i < batches.size(); i++) {
                var batch = batches.get(i);
                last = ai.index(projectId, new AiClient.IndexRequest(batch,
                        i == 0 ? List.copyOf(removedPaths) : List.of(), reset && i == 0));
                sent += batch.size();
            }
            log.info("indexed project {}: {} chunks sent, {} in index", projectId, sent, last.total());
            return sent;
        }
    }

    public void ensureIndexed(long projectId) {
        if (ai.stats(projectId).chunks() == 0) index(projectId, null, Set.of());
    }
}
