package com.codelens.service;

import com.codelens.common.ConflictException;
import com.codelens.common.NotFoundException;
import com.codelens.domain.*;
import com.codelens.graph.DependencyGraph;
import com.codelens.ingest.GitClient;
import com.codelens.ingest.ParseCache;
import com.codelens.ingest.ScannedFile;
import com.codelens.ingest.SourceScanner;
import com.codelens.metrics.MetricsCalculator;
import com.codelens.metrics.MetricsResult;
import com.codelens.parser.ProjectParser;
import com.codelens.parser.model.AnalysisFacts;
import com.codelens.parser.model.ParsedFile;
import com.codelens.repository.AnalysisRunRepository;
import com.codelens.repository.FileRow;
import com.codelens.repository.GraphStore;
import com.codelens.repository.GraphStore.StoredFile;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

// scan -> parse -> persist -> graph -> metrics; full or incremental
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
    private static final int MAX_ERROR = 2000;

    private record Outcome(int parsed, int failed, int deleted, boolean reset, Set<String> changed, Set<String> removed) {

        static final Outcome NONE = new Outcome(0, 0, 0, false, Set.of(), Set.of());

        boolean hasChanges() {
            return reset || !changed.isEmpty() || !removed.isEmpty();
        }
    }

    private final ProjectRepository projects;
    private final AnalysisRunRepository runs;
    private final SourceScanner scanner;
    private final ProjectParser parser;
    private final MetricsCalculator metrics;
    private final GraphStore store;
    private final GraphService graphs;
    private final ParseCache parseCache;
    private final GitClient git;
    private final TransactionTemplate tx;
    private final ExecutorService analysisExecutor;
    private final ExecutorService jobExecutor;
    private final ApplicationEventPublisher events;
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    public AnalysisService(ProjectRepository projects, AnalysisRunRepository runs, SourceScanner scanner,
                           ProjectParser parser, MetricsCalculator metrics, GraphStore store, GraphService graphs,
                           ParseCache parseCache, GitClient git, TransactionTemplate tx,
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
        this.parseCache = parseCache;
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

            Outcome outcome = run.getMode() == RunMode.INCREMENTAL
                    ? incremental(projectId, run, root, scanned)
                    : full(projectId, root, scanned);

            DependencyGraph graph = null;
            if (outcome.hasChanges()) {
                graph = DependencyGraph.of(store.loadNodes(projectId), store.loadEdges(projectId));
                MetricsResult result = metrics.compute(graph, analysisExecutor);
                tx.executeWithoutResult(s -> store.replaceMetrics(projectId, result));
            }

            run.setFilesTotal(scanned.size());
            run.setFilesParsed(outcome.parsed() - outcome.failed());
            run.setFilesDeleted(outcome.deleted());
            run.setEntities(store.countEntities(projectId));
            run.setEdges(store.countEdges(projectId));
            complete(run, t0, RunStatus.SUCCESS, null);

            if (graph != null) graphs.put(projectId, run.getId(), graph);
            else graphs.rekey(projectId, run.getId());

            int parseErrors = store.countParseErrors(projectId);
            updateProject(projectId, ProjectStatus.READY,
                    parseErrors == 0 ? null : parseErrors + " file(s) had parse errors", run.getCommitHash());
            if (outcome.hasChanges()) {
                events.publishEvent(new AnalysisCompletedEvent(projectId, run.getId(), run.getMode(), outcome.reset(),
                        outcome.changed(), outcome.removed()));
            }
            log.info("{} analysis {} project {}: parsed {}, deleted {}, {} entities, {} edges in {} ms",
                    run.getMode(), run.getId(), projectId, outcome.parsed(), outcome.deleted(), run.getEntities(),
                    run.getEdges(), run.getDurationMs());
        } catch (Exception e) {
            log.error("analysis {} project {} failed", run.getId(), projectId, e);
            complete(run, t0, RunStatus.FAILED, message(e));
            updateProject(projectId, ProjectStatus.FAILED, message(e), null);
        } finally {
            running.remove(projectId);
        }
        return run;
    }

    private Outcome full(long projectId, Path root, List<ScannedFile> scanned) {
        List<String> paths = scanned.stream().map(ScannedFile::path).toList();
        List<ParsedFile> parsed = parser.parse(root, paths, analysisExecutor);
        AnalysisFacts facts = parser.resolve(parsed, analysisExecutor);
        tx.executeWithoutResult(s -> writeFull(projectId, scanned, facts));
        parseCache.save(projectId, cacheEntries(parsed, scanned));
        return new Outcome(parsed.size(), failures(parsed), 0, true, new TreeSet<>(paths), Set.of());
    }

    private Outcome incremental(long projectId, AnalysisRun run, Path root, List<ScannedFile> scanned) {
        Map<String, StoredFile> stored = store.loadFiles(projectId);
        if (stored.isEmpty()) {
            log.info("project {} has no stored analysis, running full", projectId);
            run.setMode(RunMode.FULL);
            return full(projectId, root, scanned);
        }

        var hashes = stored.values().stream().collect(Collectors.toMap(StoredFile::path, StoredFile::sha256));
        ChangeSet changes = ChangeSet.diff(hashes, scanned);
        if (changes.isEmpty()) return Outcome.NONE;

        // edges into changed/deleted entities must be re-resolved
        var dependents = new TreeSet<>(store.dependentFilePaths(projectId,
                fileIds(stored, union(changes.changed(), changes.deleted()))));
        dependents.removeAll(changes.changed());
        dependents.removeAll(changes.deleted());

        Set<String> fresh = union(changes.added(), changes.changed());
        Map<String, ParseCache.Entry> cache = parseCache.load(projectId);
        var parsed = new ArrayList<ParsedFile>();
        var toParse = new ArrayList<String>();
        for (ScannedFile f : scanned) {
            ParseCache.Entry hit = cache.get(f.path());
            if (!fresh.contains(f.path()) && hit != null && hit.sha256().equals(f.sha256())) parsed.add(hit.file());
            else toParse.add(f.path());
        }
        List<ParsedFile> reparsed = parser.parse(root, toParse, analysisExecutor);
        parsed.addAll(reparsed);
        parsed.sort(Comparator.comparing(ParsedFile::path));

        Set<String> affected = union(fresh, dependents);
        AnalysisFacts facts = parser.resolve(parsed, affected::contains, analysisExecutor);
        tx.executeWithoutResult(s -> writeIncremental(projectId, stored, changes, dependents, scanned, facts));
        parseCache.save(projectId, cacheEntries(parsed, scanned));

        log.info("project {} changes: +{} ~{} -{}, {} dependent file(s) re-resolved", projectId,
                changes.added().size(), changes.changed().size(), changes.deleted().size(), dependents.size());
        return new Outcome(reparsed.size(), failures(reparsed), changes.deleted().size(), false, fresh, changes.deleted());
    }

    private void writeFull(long projectId, List<ScannedFile> scanned, AnalysisFacts facts) {
        store.deleteProjectData(projectId);
        Map<String, Long> fileIds = store.insertFiles(projectId, fileRows(scanned, facts.files()).values());
        Map<String, Long> entityIds = store.insertEntities(projectId, facts.entities(), fileIds, Map.of());
        store.insertEdges(projectId, facts.edges(), entityIds, fileIds);
    }

    private void writeIncremental(long projectId, Map<String, StoredFile> stored, ChangeSet changes,
                                  Set<String> dependents, List<ScannedFile> scanned, AnalysisFacts facts) {
        store.deleteFiles(projectId, fileIds(stored, changes.deleted()));
        store.deleteEntitiesOfFiles(projectId, fileIds(stored, changes.changed()));
        store.deleteEdgesOfFiles(projectId, fileIds(stored, dependents));

        Map<String, FileRow> rows = fileRows(scanned, facts.files());
        store.updateFiles(projectId, changes.changed().stream().map(rows::get).filter(Objects::nonNull).toList());
        store.insertFiles(projectId, changes.added().stream().map(rows::get).filter(Objects::nonNull).toList());

        var fileIds = new HashMap<String, Long>();
        store.loadFiles(projectId).forEach((path, f) -> fileIds.put(path, f.id()));
        Map<String, Long> known = store.loadEntityIds(projectId);
        Set<String> fresh = union(changes.added(), changes.changed());
        var entities = facts.entities().stream()
                .filter(e -> fresh.contains(e.filePath()) && !known.containsKey(e.qualifiedName()))
                .toList();
        Map<String, Long> entityIds = store.insertEntities(projectId, entities, fileIds, known);
        store.insertEdges(projectId, facts.edges(), entityIds, fileIds);
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

    private static Map<String, FileRow> fileRows(List<ScannedFile> scanned, List<ParsedFile> parsed) {
        Map<String, ParsedFile> byPath = parsed.stream().collect(Collectors.toMap(ParsedFile::path, f -> f));
        var rows = new LinkedHashMap<String, FileRow>();
        for (ScannedFile s : scanned) {
            ParsedFile f = byPath.get(s.path());
            if (f != null) {
                rows.put(s.path(), new FileRow(s.path(), s.module(), f.packageName(), s.sha256(), f.loc(), f.test(),
                        f.parseError()));
            }
        }
        return rows;
    }

    private static Map<String, ParseCache.Entry> cacheEntries(List<ParsedFile> parsed, List<ScannedFile> scanned) {
        Map<String, String> sha = scanned.stream().collect(Collectors.toMap(ScannedFile::path, ScannedFile::sha256));
        var entries = new HashMap<String, ParseCache.Entry>();
        for (ParsedFile f : parsed) {
            String hash = sha.get(f.path());
            if (hash != null) entries.put(f.path(), new ParseCache.Entry(hash, f));
        }
        return entries;
    }

    private static List<Long> fileIds(Map<String, StoredFile> stored, Collection<String> paths) {
        return paths.stream().map(stored::get).filter(Objects::nonNull).map(StoredFile::id).toList();
    }

    private static int failures(List<ParsedFile> files) {
        return (int) files.stream().filter(f -> f.parseError() != null).count();
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... sets) {
        var out = new TreeSet<String>();
        for (Set<String> s : sets) out.addAll(s);
        return out;
    }

    private static String message(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return m.length() <= MAX_ERROR ? m : m.substring(0, MAX_ERROR);
    }
}
