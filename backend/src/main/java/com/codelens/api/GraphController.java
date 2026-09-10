package com.codelens.api;

import com.codelens.api.dto.GraphView;
import com.codelens.api.dto.PathResult;
import com.codelens.graph.EntityRef;
import com.codelens.service.GraphQueryService;
import com.codelens.service.GraphQueryService.Expand;
import com.codelens.service.GraphQueryService.Level;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/graph")
@Tag(name = "Graph", description = "Dependency graph queries")
public class GraphController {

    private final GraphQueryService graph;

    public GraphController(GraphQueryService graph) {
        this.graph = graph;
    }

    @GetMapping
    @Operation(summary = "Subgraph around a focus entity, or the riskiest nodes without focus")
    public GraphView view(@PathVariable long projectId,
                          @RequestParam(defaultValue = "TYPE") Level level,
                          @RequestParam(required = false) Long focus,
                          @RequestParam(defaultValue = "2") int depth,
                          @RequestParam(name = "direction", defaultValue = "BOTH") Expand expand,
                          @RequestParam(defaultValue = "150") int limit,
                          @RequestParam(defaultValue = "false") boolean includeTests) {
        return graph.view(projectId, level, focus, depth, expand, limit, includeTests);
    }

    @GetMapping("/path")
    @Operation(summary = "Dependency path between two entities, in either direction")
    public PathResult path(@PathVariable long projectId, @RequestParam long from, @RequestParam long to,
                           @RequestParam(defaultValue = "12") int maxDepth,
                           @RequestParam(defaultValue = "false") boolean all) {
        return graph.path(projectId, from, to, maxDepth, all);
    }

    @GetMapping("/cycles")
    public List<List<EntityRef>> cycles(@PathVariable long projectId) {
        return graph.cycles(projectId);
    }
}
