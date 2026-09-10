package com.codelens.ai;

import com.codelens.api.dto.AiSourceDto;
import com.codelens.api.dto.AskResponseDto;
import com.codelens.api.dto.OverviewDto;
import com.codelens.domain.CodeEntity;
import com.codelens.domain.EntityKind;
import com.codelens.graph.DependencyGraph;
import com.codelens.graph.DependencyGraph.Adj;
import com.codelens.graph.EntityRef;
import com.codelens.graph.GraphNode;
import com.codelens.impact.ImpactResult;
import com.codelens.repository.CodeEntityRepository;
import com.codelens.repository.EntityMetricRepository;
import com.codelens.service.GraphService;
import com.codelens.service.ImpactService;
import com.codelens.service.MetricsQueryService;
import com.codelens.service.ProjectService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// question -> graph facts for mentioned entities -> rag answer
@Service
public class AskService {

    private static final Pattern IDENT = Pattern.compile("[A-Za-z_$][\\w$]*(?:[.#][A-Za-z_$][\\w$]*)?");
    private static final Pattern ROUTE = Pattern.compile("/[\\w{}/.-]+");
    private static final int MAX_ENTITIES = 4;
    private static final int MAX_NEIGHBORS = 12;
    private static final int TOP_K = 8;

    private final AiClient ai;
    private final AiIndexer indexer;
    private final GraphService graphs;
    private final ImpactService impacts;
    private final MetricsQueryService metricsQueries;
    private final EntityMetricRepository metrics;
    private final CodeEntityRepository entities;
    private final ProjectService projects;

    public AskService(AiClient ai, AiIndexer indexer, GraphService graphs, ImpactService impacts,
                      MetricsQueryService metricsQueries, EntityMetricRepository metrics,
                      CodeEntityRepository entities, ProjectService projects) {
        this.ai = ai;
        this.indexer = indexer;
        this.graphs = graphs;
        this.impacts = impacts;
        this.metricsQueries = metricsQueries;
        this.metrics = metrics;
        this.entities = entities;
        this.projects = projects;
    }

    public AskResponseDto ask(long projectId, String question) {
        projects.get(projectId);
        DependencyGraph g = graphs.graph(projectId);
        List<Integer> mentioned = mentioned(g, question);

        var facts = new LinkedHashMap<String, Object>();
        facts.put("project", projectFacts(projectId, g));
        facts.put("entities", mentioned.stream().map(i -> entityFacts(projectId, g, i)).toList());

        indexer.ensureIndexed(projectId);
        var result = ai.ask(projectId, new AiClient.AskRequest(question, facts, focus(g, mentioned), TOP_K));
        return new AskResponseDto(result.answer(), sources(projectId, result.sources()), facts, result.model(),
                result.cached(), result.generatedBy());
    }

    List<AiSourceDto> sources(long projectId, List<AiClient.Source> sources) {
        if (sources == null) return List.of();
        return sources.stream().map(s -> new AiSourceDto(s.ref(), s.path(), s.label(), s.qualifiedName(), s.startLine(),
                s.endLine(), s.score(), entities.findByProjectIdAndQualifiedName(projectId, s.qualifiedName())
                .map(CodeEntity::getId).orElse(null))).toList();
    }

    // class names, Type#method / Type.method, and routes like /api/users
    private List<Integer> mentioned(DependencyGraph g, String question) {
        var types = new HashMap<String, List<Integer>>();
        var endpoints = new ArrayList<Integer>();
        for (int i = 0; i < g.size(); i++) {
            GraphNode n = g.node(i);
            if (n.kind().isType()) types.computeIfAbsent(n.name(), k -> new ArrayList<>()).add(i);
            else if (n.kind() == EntityKind.ENDPOINT) endpoints.add(i);
        }

        var found = new LinkedHashSet<Integer>();
        var m = IDENT.matcher(question);
        while (m.find() && found.size() < MAX_ENTITIES) {
            String token = m.group();
            int sep = Math.max(token.indexOf('#'), token.indexOf('.'));
            if (sep > 0) {
                String owner = token.substring(0, sep);
                String member = token.substring(sep + 1);
                for (int t : types.getOrDefault(owner, List.of())) {
                    for (int member_ : g.members(t)) {
                        if (g.node(member_).name().equals(member)) found.add(member_);
                    }
                }
                if (!found.isEmpty()) continue;
                token = owner;
            }
            for (int t : types.getOrDefault(token, List.of())) {
                if (found.size() < MAX_ENTITIES) found.add(t);
            }
        }
        var routes = ROUTE.matcher(question);
        while (routes.find() && found.size() < MAX_ENTITIES) {
            String route = routes.group();
            endpoints.stream().filter(i -> g.node(i).name().contains(route)).limit(2).forEach(found::add);
        }
        return List.copyOf(found);
    }

    private Map<String, Object> projectFacts(long projectId, DependencyGraph g) {
        OverviewDto overview = metricsQueries.overview(projectId);
        var f = new LinkedHashMap<String, Object>();
        f.put("stats", overview.stats());
        f.put("roles", overview.roles());
        f.put("riskiest", overview.topRisks().stream().limit(5)
                .map(h -> h.entity().label() + " (risk " + Math.round(h.metrics().riskScore()) + ", "
                        + h.metrics().dependents() + " dependents)")
                .toList());
        f.put("endpoints", g.nodes().stream().filter(n -> n.kind() == EntityKind.ENDPOINT).limit(25)
                .map(n -> n.name() + " -> " + n.qualifiedName().substring(n.qualifiedName().indexOf('@') + 1))
                .toList());
        f.put("dependencyCycles", overview.cycles().size());
        return f;
    }

    private Map<String, Object> entityFacts(long projectId, DependencyGraph g, int i) {
        GraphNode n = g.node(i);
        var f = new LinkedHashMap<String, Object>();
        f.put("qualifiedName", n.qualifiedName());
        f.put("kind", n.kind());
        f.put("role", EntityRef.of(g, i).role());
        f.put("file", n.filePath());
        f.put("module", n.module());
        metrics.findById(n.id()).ifPresent(m -> f.put("metrics", Map.of(
                "fanIn", m.getFanIn(), "fanOut", m.getFanOut(), "transitiveDependents", m.getDependents(),
                "transitiveDependencies", m.getDependencies(), "depth", m.getDepth(),
                "complexity", m.getComplexity(), "riskScore", m.getRiskScore())));
        f.put("dependents", neighbors(g, g.in(i)));
        f.put("dependencies", neighbors(g, g.out(i)));
        if (n.kind().isType()) {
            f.put("members", Arrays.stream(g.members(i)).mapToObj(g::node)
                    .filter(c -> c.kind() != EntityKind.FIELD).limit(25).map(GraphNode::label).toList());
        }

        ImpactResult impact = impacts.forEntity(projectId, n.id(), 8, false);
        var summary = new LinkedHashMap<String, Object>();
        summary.put("directDependents", impact.directDependents());
        summary.put("transitiveDependents", impact.transitiveDependents());
        summary.put("maxDepth", impact.maxDepth());
        summary.put("risk", impact.risk().level() + " " + impact.risk().score());
        summary.put("affectedEndpoints", impact.endpoints().stream().limit(10).map(a -> a.entity().label()).toList());
        summary.put("affectedTypes", impact.affectedTypes().entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, e -> e.getValue().stream().limit(8).map(EntityRef::label).toList(),
                (a, b) -> a, TreeMap::new)));
        summary.put("modules", impact.modules());
        summary.put("testsToRun", impact.tests().stream().limit(10).map(EntityRef::label).toList());
        f.put("changeImpact", summary);
        return f;
    }

    // "UserServiceImpl (implements)"
    private static List<String> neighbors(DependencyGraph g, Adj[] adj) {
        var labels = Arrays.stream(adj)
                .map(a -> g.node(a.node()).label() + " (" + a.type().name().toLowerCase().replace('_', ' ') + ")")
                .distinct()
                .sorted()
                .toList();
        if (labels.size() <= MAX_NEIGHBORS) return labels;
        var out = new ArrayList<>(labels.subList(0, MAX_NEIGHBORS));
        out.add("... and " + (labels.size() - MAX_NEIGHBORS) + " more");
        return out;
    }

    // pin mentioned code and its closest callers for retrieval
    private static List<String> focus(DependencyGraph g, List<Integer> mentioned) {
        var out = new LinkedHashSet<String>();
        for (int i : mentioned) {
            out.add(g.node(i).qualifiedName());
            Arrays.stream(g.members(i)).mapToObj(g::node).filter(c -> c.kind() == EntityKind.METHOD).limit(6)
                    .forEach(c -> out.add(c.qualifiedName()));
            Arrays.stream(g.in(i)).map(a -> g.node(a.node())).filter(c -> c.kind() != EntityKind.ENDPOINT).limit(4)
                    .forEach(c -> out.add(c.qualifiedName()));
        }
        return List.copyOf(out);
    }
}
