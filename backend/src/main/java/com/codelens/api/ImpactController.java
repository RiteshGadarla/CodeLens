package com.codelens.api;

import com.codelens.impact.ImpactResult;
import com.codelens.service.ImpactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/impact")
@Tag(name = "Impact", description = "Change impact analysis")
public class ImpactController {

    private final ImpactService impact;

    public ImpactController(ImpactService impact) {
        this.impact = impact;
    }

    @GetMapping("/{entityId}")
    @Operation(summary = "What is affected if this class, method or field changes")
    public ImpactResult entity(@PathVariable long projectId, @PathVariable long entityId,
                               @RequestParam(defaultValue = "10") int depth,
                               @RequestParam(defaultValue = "false") boolean includeTests) {
        return impact.forEntity(projectId, entityId, depth, includeTests);
    }

    @GetMapping("/file")
    @Operation(summary = "What is affected if this file changes")
    public ImpactResult file(@PathVariable long projectId, @RequestParam String path,
                             @RequestParam(defaultValue = "10") int depth,
                             @RequestParam(defaultValue = "false") boolean includeTests) {
        return impact.forFile(projectId, path, depth, includeTests);
    }
}
