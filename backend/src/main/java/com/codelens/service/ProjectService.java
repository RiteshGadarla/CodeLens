package com.codelens.service;

import com.codelens.common.ConflictException;
import com.codelens.common.NotFoundException;
import com.codelens.domain.Project;
import com.codelens.domain.SourceType;
import com.codelens.ingest.Workspace;
import com.codelens.ingest.ZipExtractor;
import com.codelens.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ProjectService {

    private static final Pattern GIT_URL = Pattern.compile("^(https?://|ssh://|git@|file:/).+");
    private static final String PENDING = "pending";

    private final ProjectRepository projects;
    private final Workspace workspace;
    private final ZipExtractor zips;
    private final AnalysisService analysis;
    private final GraphService graphs;
    private final ApplicationEventPublisher events;
    private final boolean allowLocalPaths;

    public ProjectService(ProjectRepository projects, Workspace workspace, ZipExtractor zips,
                          AnalysisService analysis, GraphService graphs, ApplicationEventPublisher events,
                          @Value("${codelens.allow-local-paths:true}") boolean allowLocalPaths) {
        this.projects = projects;
        this.workspace = workspace;
        this.zips = zips;
        this.analysis = analysis;
        this.graphs = graphs;
        this.events = events;
        this.allowLocalPaths = allowLocalPaths;
    }

    public List<Project> list(long ownerId) {
        return projects.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    public Project get(long id) {
        return projects.findById(id).orElseThrow(() -> new NotFoundException("project", id));
    }

    public Project createLocal(Long ownerId, String name, String path) {
        requireLocalPaths();
        if (path == null || path.isBlank()) throw new IllegalArgumentException("path is required");
        Path dir = Path.of(path).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) throw new IllegalArgumentException("directory not found: " + path);
        return projects.save(newProject(ownerId, name, String.valueOf(dir.getFileName()), SourceType.LOCAL,
                dir.toString(), dir.toString(), null));
    }

    public Project createGit(Long ownerId, String name, String url, String branch) {
        if (url == null || !GIT_URL.matcher(url.trim()).matches()) {
            throw new IllegalArgumentException("unsupported git url: " + url);
        }
        String clean = url.trim();
        if (clean.startsWith("file:")) requireLocalPaths();
        Project p = projects.save(newProject(ownerId, name, repoName(clean), SourceType.GIT, clean, PENDING, branch));
        p.setLocalPath(workspace.checkoutDir(p.getId()).toString());
        return projects.save(p);
    }

    public Project createUpload(Long ownerId, String name, String filename, InputStream zip) {
        Project p = projects.save(newProject(ownerId, name, stripExtension(filename), SourceType.UPLOAD, filename,
                PENDING, null));
        try {
            Path dir = workspace.checkoutDir(p.getId());
            zips.extract(zip, dir);
            p.setLocalPath(Workspace.unwrap(dir).toString());
            return projects.save(p);
        } catch (IOException | RuntimeException e) {
            projects.delete(p);
            workspace.deleteQuietly(p.getId());
            if (e instanceof IllegalArgumentException iae) throw iae;
            throw new IllegalArgumentException("invalid archive: " + e.getMessage(), e);
        }
    }

    public void delete(long id) {
        Project p = get(id);
        if (analysis.isRunning(id)) throw new ConflictException("analysis is running for project " + id);
        projects.delete(p);
        graphs.evict(id);
        // local sources live outside the workspace; only cache is removed
        workspace.deleteQuietly(id);
        events.publishEvent(new ProjectDeletedEvent(id));
    }

    private void requireLocalPaths() {
        if (!allowLocalPaths) throw new IllegalArgumentException("local paths are disabled on this server");
    }

    private static Project newProject(Long ownerId, String name, String fallback, SourceType type, String uri,
                                      String path, String branch) {
        var p = new Project();
        p.setOwnerId(ownerId);
        String n = name == null || name.isBlank() ? fallback : name.trim();
        p.setName(n.length() > 200 ? n.substring(0, 200) : n);
        p.setSourceType(type);
        p.setSourceUri(uri);
        p.setLocalPath(path);
        p.setBranch(branch == null || branch.isBlank() ? null : branch.trim());
        return p;
    }

    static String repoName(String url) {
        String s = url.replaceAll("/+$", "");
        s = s.substring(Math.max(s.lastIndexOf('/'), s.lastIndexOf(':')) + 1);
        return s.endsWith(".git") ? s.substring(0, s.length() - 4) : s;
    }

    private static String stripExtension(String filename) {
        if (filename == null || filename.isBlank()) return "upload";
        String base = Path.of(filename).getFileName().toString();
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }
}
