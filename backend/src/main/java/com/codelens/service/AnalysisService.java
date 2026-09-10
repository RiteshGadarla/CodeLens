package com.codelens.service;

import com.codelens.common.ConflictException;
import com.codelens.common.NotFoundException;
import com.codelens.domain.*;
import com.codelens.graph.DependencyGraph;
import com.codelens.ingest.GitClient;
import com.codelens.ingest.ScannedFile;
import com.codelens.ingest.SourceScanner;
import com.codelens.metrics.MetricsCalculator;
import com.codelens.metrics.MetricsResult;
import com.codelens.parser.ProjectParser;
import com.codelens.parser.model.AnalysisFacts;
import com.codelens.repository.AnalysisRunRepository;
import com.codelens.repository.FileRow;
import com.codelens.repository.GraphStore;
import com.codelens.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

// scan -> parse -> persist -> graph -> metrics
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
    private static final int MAX_ERROR = 2000;

    private final ProjectRepository projects;
    private final AnalysisRunRepository runs;
    private final SourceScanner scanner;
    private final ProjectParser parser;
    private final MetricsCalculator metrics;
    private final GraphStore store;
    private final GraphService graphs;
    private final GitClient git;
    private final TransactionTemplate tx;
    private final ExecutorService analysisExecutor;
    private final ExecutorService jobExecutor;
    private final ApplicationEventPublisher events;
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    public AnalysisService(ProjectRepository projects, AnalysisRunRepository runs, SourceScanner scanner,
                           ProjectParser parser, MetricsCalculator metrics, GraphStore store, GraphService graphs,
                           GitClient git, TransactionTemplate tx,
                           @Qualifier("analysisExecutor") ExecutorService analysisExecutor,
                           @Qualifier("jobExecutor") ExecutorService jobExecutor,
                           ApplicationEventPublisher events) {
        this.projects = projects;
        this.runs = runs;
        this.scanner = scanner;
        this.parser = parser;
        this.metrics = metrics;
        this.store = store;
        this.graphs = graphs;
        this.git = git;
        this.tx = tx;
        this.analysisExecutor = analysisExecutor;
        this.jobExecutor = jobExecutor;
        this.events = events;
    }

    public boolean isRunning(long projectId) {
        return running.contains(projectId);
    }

    // async
    public AnalysisRun start(long projectId, RunMode mode) {
        AnalysisRun run = begin(projectId, mode);
        try {
            jobExecutor.submit(() -> execute(projectId, run));
        } catch (RuntimeException e) {
            running.remove(projectId);
            throw e;
        }
        return run;
    }

    // blocking
    public AnalysisRun runNow(long projectId, RunMode mode) {
        return execute(projectId, begin(projectId, mode));
    }

    private AnalysisRun begin(long projectId, RunMode mode) {
        Project project = projects.findById(projectId).orElseThrow(() -> new NotFoundException("project", projectId));
        if (!running.add(projectId)) throw new ConflictException("analysis already running for project " + projectId);
        try {
            project.setStatus(ProjectStatus.ANALYZING);
            project.setStatusMessage(null);
            projects.save(project);
            return runs.save(new AnalysisRun(projectId, mode));
        } catch (RuntimeException e) {
            running.remove(projectId);
            throw e;
        }
    }

    private AnalysisRun execute(long projectId, AnalysisRun run) {
        long t0 = System.nanoTime();
        try {
            Project project = projects.findById(projectId).orElseThrow(() -> new NotFoundException("project", projectId));
            Path root = syncSource(project);
            run.setCommitHash(git.headCommit(root));

            List<ScannedFile> scanned = scanner.scan(root);
            AnalysisFacts facts = parser.analyze(root, scanned.stream().map(ScannedFile::path).toList(), analysisExecutor);
            int edges = Objects.requireNonNull(tx.execute(s -> writeFull(projectId, scanned, facts)));

            DependencyGraph graph = DependencyGraph.of(store.loadNodes(projectId), store.loadEdges(projectId));
            MetricsResult result = metrics.compute(graph, analysisExecutor);
            tx.executeWithoutResult(s -> store.replaceMetrics(projectId, result));

            long failed = facts.files().stream().filter(f -> f.parseError() != null).count();
            run.setFilesTotal(scanned.size());
            run.setFilesParsed((int) (facts.files().size() - failed));
            run.setEntities(facts.entities().size());
            run.setEdges(edges);
            complete(run, t0, RunStatus.SUCCESS, null);

            graphs.put(projectId, run.getId(), graph);
            updateProject(projectId, ProjectStatus.READY,
                    failed == 0 ? null : failed + " file(s) had parse errors", run.getCommitHash());
            events.publishEvent(new AnalysisCompletedEvent(projectId, run.getId(), run.getMode()));
            log.info("analysis {} project {}: {} files, {} entities, {} edges in {} ms", run.getId(), projectId,
                    scanned.size(), facts.entities().size(), edges, run.getDurationMs());
        } catch (Exception e) {
            log.error("analysis {} project {} failed", run.getId(), projectId, e);
            complete(run, t0, RunStatus.FAILED, message(e));
            updateProject(projectId, ProjectStatus.FAILED, message(e), null);
        } finally {
            running.remove(projectId);
        }
        return run;
    }

    private Path syncSource(Project p) throws Exception {
        Path root = Path.of(p.getLocalPath());
        if (p.getSourceType() == SourceType.GIT) {
            if (Files.isDirectory(root.resolve(".git"))) git.pull(root);
            else git.cloneRepo(p.getSourceUri(), p.getBranch(), root);
        }
        if (!Files.isDirectory(root)) throw new IllegalStateException("source directory not found: " + root);
        return root;
    }

    private int writeFull(long projectId, List<ScannedFile> scanned, AnalysisFacts facts) {
        store.deleteProjectData(projectId);
        Map<String, ScannedFile> byPath = scanned.stream()
                .collect(Collectors.toMap(ScannedFile::path, Function.identity()));
        var rows = facts.files().stream().map(f -> {
            ScannedFile s = byPath.get(f.path());
            return new FileRow(f.path(), s.module(), f.packageName(), s.sha256(), f.loc(), f.test(), f.parseError());
        }).toList();
        Map<String, Long> fileIds = store.insertFiles(projectId, rows);
        Map<String, Long> entityIds = store.insertEntities(projectId, facts.entities(), fileIds, Map.of());
        return store.insertEdges(projectId, facts.edges(), entityIds, fileIds);
    }

    private void complete(AnalysisRun run, long t0, RunStatus status, String error) {
        run.setStatus(status);
        run.setError(error);
        run.setFinishedAt(Instant.now());
        run.setDurationMs((System.nanoTime() - t0) / 1_000_000);
        runs.save(run);
    }

    private void updateProject(long projectId, ProjectStatus status, String message, String commit) {
        projects.findById(projectId).ifPresent(p -> {
            p.setStatus(status);
            p.setStatusMessage(message);
            if (commit != null) p.setLastCommit(commit);
            projects.save(p);
        });
    }

    private static String message(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return m.length() <= MAX_ERROR ? m : m.substring(0, MAX_ERROR);
    }
}
