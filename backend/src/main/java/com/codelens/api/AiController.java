package com.codelens.api;

import com.codelens.ai.AiClient;
import com.codelens.ai.AiIndexer;
import com.codelens.ai.AskService;
import com.codelens.ai.ReportService;
import com.codelens.api.dto.AskRequestDto;
import com.codelens.api.dto.AskResponseDto;
import com.codelens.api.dto.ReportDto;
import com.codelens.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}")
@Tag(name = "AI", description = "Grounded Q&A and change-impact reports")
public class AiController {

    private final AskService asks;
    private final ReportService reports;
    private final AiIndexer indexer;
    private final AiClient ai;
    private final ProjectService projects;

    public AiController(AskService asks, ReportService reports, AiIndexer indexer, AiClient ai, ProjectService projects) {
        this.asks = asks;
        this.reports = reports;
        this.indexer = indexer;
        this.ai = ai;
        this.projects = projects;
    }

    @PostMapping("/ask")
    @Operation(summary = "Answer a question using graph facts and retrieved source")
    public AskResponseDto ask(@PathVariable long projectId, @Valid @RequestBody AskRequestDto body) {
        return asks.ask(projectId, body.question());
    }

    @PostMapping("/reports/impact/{entityId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate and store a change-impact report")
    public ReportDto impactReport(@PathVariable long projectId, @PathVariable long entityId) {
        return reports.impactReport(projectId, entityId);
    }

    @GetMapping("/reports")
    public List<ReportDto> reports(@PathVariable long projectId) {
        return reports.list(projectId);
    }

    @GetMapping("/reports/{reportId}")
    public ReportDto report(@PathVariable long projectId, @PathVariable long reportId) {
        return reports.get(projectId, reportId);
    }

    @PostMapping("/ai/reindex")
    public Map<String, Object> reindex(@PathVariable long projectId) {
        projects.get(projectId);
        return Map.of("chunks", indexer.index(projectId, null, java.util.Set.of()));
    }

    @GetMapping("/ai/status")
    public Map<String, Object> status(@PathVariable long projectId) {
        projects.get(projectId);
        return Map.of("service", ai.health(), "index", ai.stats(projectId));
    }
}
