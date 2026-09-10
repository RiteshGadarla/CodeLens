package com.codelens.api;

import com.codelens.api.dto.CreateProjectRequest;
import com.codelens.api.dto.ProjectDto;
import com.codelens.api.dto.RunDto;
import com.codelens.domain.Project;
import com.codelens.domain.RunMode;
import com.codelens.repository.AnalysisRunRepository;
import com.codelens.service.AnalysisService;
import com.codelens.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Repository ingestion and analysis runs")
public class ProjectController {

    private final ProjectService projects;
    private final AnalysisService analysis;
    private final AnalysisRunRepository runs;

    public ProjectController(ProjectService projects, AnalysisService analysis, AnalysisRunRepository runs) {
        this.projects = projects;
        this.analysis = analysis;
        this.runs = runs;
    }

    @GetMapping
    public List<ProjectDto> list() {
        return projects.list().stream().map(this::dto).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a local directory or git repository")
    public ProjectDto create(@Valid @RequestBody CreateProjectRequest req) {
        Project p = switch (req.sourceType()) {
            case LOCAL -> projects.createLocal(req.name(), req.path());
            case GIT -> projects.createGit(req.name(), req.url(), req.branch());
            case UPLOAD -> throw new IllegalArgumentException("upload archives with POST /api/projects/upload");
        };
        if (!Boolean.FALSE.equals(req.analyze())) analysis.start(p.getId(), RunMode.FULL);
        return dto(projects.get(p.getId()));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a zipped source tree")
    public ProjectDto upload(@RequestParam MultipartFile file, @RequestParam(required = false) String name,
                             @RequestParam(defaultValue = "true") boolean analyze) {
        Project p;
        try {
            p = projects.createUpload(name, file.getOriginalFilename(), file.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (analyze) analysis.start(p.getId(), RunMode.FULL);
        return dto(projects.get(p.getId()));
    }

    @GetMapping("/{id}")
    public ProjectDto get(@PathVariable long id) {
        return dto(projects.get(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        projects.delete(id);
    }

    @PostMapping("/{id}/analyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Start an analysis run; incremental falls back to full on first run")
    public RunDto analyze(@PathVariable long id, @RequestParam(defaultValue = "INCREMENTAL") RunMode mode) {
        return RunDto.of(analysis.start(id, mode));
    }

    @GetMapping("/{id}/runs")
    public List<RunDto> runs(@PathVariable long id) {
        projects.get(id);
        return runs.findTop20ByProjectIdOrderByStartedAtDesc(id).stream().map(RunDto::of).toList();
    }

    private ProjectDto dto(Project p) {
        return ProjectDto.of(p, runs.findFirstByProjectIdOrderByStartedAtDesc(p.getId()).orElse(null),
                analysis.isRunning(p.getId()));
    }
}
