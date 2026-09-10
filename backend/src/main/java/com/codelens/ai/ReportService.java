package com.codelens.ai;

import com.codelens.api.dto.AiSourceDto;
import com.codelens.api.dto.ReportDto;
import com.codelens.common.NotFoundException;
import com.codelens.domain.Report;
import com.codelens.domain.ReportKind;
import com.codelens.graph.DependencyGraph;
import com.codelens.graph.EntityRef;
import com.codelens.graph.PathStep;
import com.codelens.impact.ImpactResult;
import com.codelens.repository.ReportRepository;
import com.codelens.service.GraphService;
import com.codelens.service.ImpactService;
import com.codelens.service.ProjectService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

// deterministic impact analysis + llm narrative, persisted together
@Service
public class ReportService {

    private static final int TOP_K = 6;
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<AiSourceDto>> SOURCES = new TypeReference<>() {
    };

    private final AiClient ai;
    private final AiIndexer indexer;
    private final AskService asks;
    private final ImpactService impacts;
    private final GraphService graphs;
    private final ReportRepository reports;
    private final ProjectService projects;
    private final ObjectMapper json;

    public ReportService(AiClient ai, AiIndexer indexer, AskService asks, ImpactService impacts, GraphService graphs,
                         ReportRepository reports, ProjectService projects, ObjectMapper json) {
        this.ai = ai;
        this.indexer = indexer;
        this.asks = asks;
        this.impacts = impacts;
        this.graphs = graphs;
        this.reports = reports;
        this.projects = projects;
        this.json = json;
    }

    public ReportDto impactReport(long projectId, long entityId) {
        ImpactResult impact = impacts.forEntity(projectId, entityId, 10, false);
        indexer.ensureIndexed(projectId);
        var result = ai.report(projectId, new AiClient.ReportRequest(compact(impact), focus(projectId, impact), TOP_K));
        List<AiSourceDto> sources = asks.sources(projectId, result.sources());

        var report = new Report();
        report.setProjectId(projectId);
        report.setEntityId(entityId);
        report.setKind(ReportKind.IMPACT);
        var stored = new LinkedHashMap<String, Object>();
        stored.put("impact", json.convertValue(impact, MAP));
        stored.put("sources", json.convertValue(sources, new TypeReference<List<Object>>() {
        }));
        report.setFacts(stored);
        report.setSummary(result.summary());
        report.setModel(result.model());
        return dto(reports.save(report));
    }

    public List<ReportDto> list(long projectId) {
        projects.get(projectId);
        return reports.findTop20ByProjectIdOrderByCreatedAtDesc(projectId).stream().map(this::dto).toList();
    }

    public ReportDto get(long projectId, long reportId) {
        return reports.findById(reportId)
                .filter(r -> r.getProjectId() == projectId)
                .map(this::dto)
                .orElseThrow(() -> new NotFoundException("report", reportId));
    }

    private ReportDto dto(Report r) {
        ImpactResult impact = json.convertValue(r.getFacts().get("impact"), ImpactResult.class);
        List<AiSourceDto> sources = json.convertValue(r.getFacts().getOrDefault("sources", List.of()), SOURCES);
        return new ReportDto(r.getId(), r.getEntityId(), r.getKind(), impact, r.getSummary(), sources, r.getModel(),
                r.getCreatedAt());
    }

    // prompt-sized view of the impact result
    private static Map<String, Object> compact(ImpactResult i) {
        var f = new LinkedHashMap<String, Object>();
        f.put("target", Map.of("label", i.target().label(), "qualifiedName", i.target().qualifiedName(),
                "kind", i.target().kind(), "role", String.valueOf(i.target().role())));
        f.put("risk", Map.of("score", i.risk().score(), "level", i.risk().level(), "factors", i.risk().factors()));
        f.put("directDependents", i.directDependents());
        f.put("transitiveDependents", i.transitiveDependents());
        f.put("maxDepth", i.maxDepth());
        f.put("affectedEndpoints", i.endpoints().stream().limit(15).map(a -> a.entity().label()).toList());
        f.put("affectedTypes", i.affectedTypes().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> e.getValue().stream().limit(10).map(EntityRef::label).toList(), (a, b) -> a, TreeMap::new)));
        f.put("modules", i.modules());
        f.put("testsToRun", i.tests().stream().limit(15).map(EntityRef::label).toList());
        f.put("affected", i.affected().stream().limit(25).map(a -> Map.of(
                "entity", a.entity().label(), "depth", a.depth(), "via", a.via(), "path", path(a.path()))).toList());
        f.put("truncated", i.truncated());
        return f;
    }

    private static String path(List<PathStep> steps) {
        var sb = new StringBuilder();
        for (PathStep s : steps) {
            if (!sb.isEmpty()) sb.append(" <-").append(s.dispatch() ? "dispatch" : String.valueOf(s.edge()).toLowerCase()).append("- ");
            sb.append(s.label());
        }
        return sb.toString();
    }

    private List<String> focus(long projectId, ImpactResult impact) {
        DependencyGraph g = graphs.graph(projectId);
        var out = new LinkedHashSet<String>();
        out.add(impact.target().qualifiedName());
        impact.affected().stream().filter(a -> a.depth() == 1).limit(5).forEach(a -> out.add(a.entity().qualifiedName()));
        // handler methods behind affected endpoints
        impact.endpoints().stream().limit(3).forEach(a -> {
            List<PathStep> p = a.path();
            if (p.size() >= 2) g.find(p.get(p.size() - 2).id()).ifPresent(n -> out.add(n.qualifiedName()));
        });
        return List.copyOf(out);
    }
}
