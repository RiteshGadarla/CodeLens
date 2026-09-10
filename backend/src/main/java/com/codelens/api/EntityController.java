package com.codelens.api;

import com.codelens.api.dto.EntityDetail;
import com.codelens.api.dto.EntitySummary;
import com.codelens.api.dto.SourceDto;
import com.codelens.domain.EntityKind;
import com.codelens.service.EntityQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/entities")
@Tag(name = "Entities", description = "Code entity search and details")
public class EntityController {

    private final EntityQueryService entities;

    public EntityController(EntityQueryService entities) {
        this.entities = entities;
    }

    @GetMapping("/search")
    public List<EntitySummary> search(@PathVariable long projectId, @RequestParam String q,
                                      @RequestParam(required = false) List<EntityKind> kinds,
                                      @RequestParam(defaultValue = "20") int limit) {
        return entities.search(projectId, q, kinds, limit);
    }

    @GetMapping("/{entityId}")
    public EntityDetail get(@PathVariable long projectId, @PathVariable long entityId) {
        return entities.detail(projectId, entityId);
    }

    @GetMapping("/{entityId}/source")
    public SourceDto source(@PathVariable long projectId, @PathVariable long entityId) {
        return entities.source(projectId, entityId);
    }
}
