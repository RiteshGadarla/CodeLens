package com.codelens.service;

import com.codelens.api.dto.EntityDetail;
import com.codelens.api.dto.EntityMetricDto;
import com.codelens.api.dto.EntitySummary;
import com.codelens.api.dto.SourceDto;
import com.codelens.common.NotFoundException;
import com.codelens.domain.CodeEntity;
import com.codelens.domain.EntityKind;
import com.codelens.domain.EntityMetric;
import com.codelens.domain.Project;
import com.codelens.domain.SourceFile;
import com.codelens.graph.DependencyGraph;
import com.codelens.graph.DependencyGraph.Adj;
import com.codelens.graph.EntityRef;
import com.codelens.graph.GraphNode;
import com.codelens.repository.CodeEntityRepository;
import com.codelens.repository.EntityMetricRepository;
import com.codelens.repository.SourceFileRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EntityQueryService {

    private static final int MAX_RELATIONS = 200;
    private static final int MAX_SOURCE_LINES = 2000;

    private final CodeEntityRepository entities;
    private final EntityMetricRepository metrics;
    private final SourceFileRepository files;
    private final GraphService graphs;
    private final ProjectService projects;

    public EntityQueryService(CodeEntityRepository entities, EntityMetricRepository metrics, SourceFileRepository files,
                              GraphService graphs, ProjectService projects) {
        this.entities = entities;
        this.metrics = metrics;
        this.files = files;
        this.graphs = graphs;
        this.projects = projects;
    }

    public List<EntitySummary> search(long projectId, String q, Collection<EntityKind> kinds, int limit) {
        projects.get(projectId);
        String term = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        if (term.isEmpty()) return List.of();
        String escaped = term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        var kindSet = kinds == null || kinds.isEmpty() ? EnumSet.allOf(EntityKind.class) : EnumSet.copyOf(kinds);
        var found = entities.search(projectId, kindSet, "%" + escaped + "%", term, escaped + "%",
                PageRequest.of(0, Math.clamp(limit, 1, 100)));
        return summarize(projectId, found);
    }

    public EntityDetail detail(long projectId, long entityId) {
        CodeEntity e = entity(projectId, entityId);
        DependencyGraph g = graphs.graph(projectId);
        int i = g.indexOf(entityId);
        EntityMetricDto metric = metrics.findById(entityId).map(EntityMetricDto::of).orElse(null);
        EntitySummary parent = e.getParentId() == null ? null
                : entities.findById(e.getParentId()).map(p -> summary(g, p, null)).orElse(null);
        return new EntityDetail(summary(g, e, metric == null ? null : metric.riskScore()), e.getSignature(),
                e.getVisibility(), e.getModifiers(), e.getAnnotations(), e.getHttpMethod(), e.getHttpPath(),
                e.getComplexity(), metric, parent, summarize(projectId, entities.findByParentIdOrderByStartLine(entityId)),
                i < 0 ? List.of() : relations(g, g.out(i)), i < 0 ? List.of() : relations(g, g.in(i)));
    }

    public SourceDto source(long projectId, long entityId) {
        Project project = projects.get(projectId);
        CodeEntity e = entity(projectId, entityId);
        SourceFile f = files.findById(e.getFileId()).orElseThrow(() -> new NotFoundException("file", e.getFileId()));

        Path root = Path.of(project.getLocalPath()).toAbsolutePath().normalize();
        Path file = root.resolve(f.getPath()).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new NotFoundException("source file", f.getPath());

        List<String> lines = readLines(file);
        int start = Math.max(1, e.getStartLine() == null ? 1 : e.getStartLine());
        int end = Math.min(lines.size(), e.getEndLine() == null ? lines.size() : e.getEndLine());
        end = Math.min(end, start + MAX_SOURCE_LINES - 1);
        String code = start > end ? "" : String.join("\n", lines.subList(start - 1, end));
        return new SourceDto(f.getPath(), start, Math.max(start, end), code, "java");
    }

    // order of ids is kept
    public Map<Long, EntitySummary> summariesById(long projectId, Collection<Long> ids) {
        return summarize(projectId, entities.findAllById(ids)).stream()
                .collect(Collectors.toMap(EntitySummary::id, Function.identity()));
    }

    private List<EntitySummary> summarize(long projectId, List<CodeEntity> list) {
        if (list.isEmpty()) return List.of();
        DependencyGraph g = graphs.graph(projectId);
        Map<Long, Double> risk = metrics.findAllById(list.stream().map(CodeEntity::getId).toList()).stream()
                .collect(Collectors.toMap(EntityMetric::getEntityId, EntityMetric::getRiskScore));
        return list.stream().map(e -> summary(g, e, risk.get(e.getId()))).toList();
    }

    private static EntitySummary summary(DependencyGraph g, CodeEntity e, Double risk) {
        int i = g.indexOf(e.getId());
        GraphNode n = i < 0 ? null : g.node(i);
        return new EntitySummary(e.getId(), e.getName(), n == null ? e.getName() : n.label(), e.getQualifiedName(),
                e.getKind(), i < 0 ? e.getStereotype() : EntityRef.of(g, i).role(),
                n == null ? null : n.filePath(), n == null ? null : n.module(), e.getStartLine(), e.getEndLine(), risk);
    }

    private static List<EntityDetail.Relation> relations(DependencyGraph g, Adj[] adj) {
        return Arrays.stream(adj)
                .map(a -> new EntityDetail.Relation(EntityRef.of(g, a.node()), a.type(), a.weight()))
                .sorted(Comparator.comparing((EntityDetail.Relation r) -> r.type().name())
                        .thenComparing(r -> r.entity().label()))
                .limit(MAX_RELATIONS)
                .toList();
    }

    private CodeEntity entity(long projectId, long entityId) {
        return entities.findById(entityId)
                .filter(e -> e.getProjectId() == projectId)
                .orElseThrow(() -> new NotFoundException("entity", entityId));
    }

    private static List<String> readLines(Path file) {
        try {
            try {
                return Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (MalformedInputException e) {
                return Files.readAllLines(file, StandardCharsets.ISO_8859_1);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
