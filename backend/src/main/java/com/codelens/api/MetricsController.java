package com.codelens.api;

import com.codelens.api.dto.HotspotList;
import com.codelens.api.dto.ModuleMetricList;
import com.codelens.api.dto.OverviewDto;
import com.codelens.domain.MetricLevel;
import com.codelens.service.MetricsQueryService;
import com.codelens.service.MetricsQueryService.Scope;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}")
@Tag(name = "Metrics", description = "Coupling, complexity and risk")
public class MetricsController {

    private final MetricsQueryService metrics;

    public MetricsController(MetricsQueryService metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/overview")
    public OverviewDto overview(@PathVariable long projectId) {
        return metrics.overview(projectId);
    }

    @GetMapping("/metrics/hotspots")
    public HotspotList hotspots(@PathVariable long projectId, @RequestParam(defaultValue = "ALL") Scope scope,
                                @RequestParam(defaultValue = "riskScore") String sort,
                                @RequestParam(defaultValue = "25") int limit) {
        return metrics.hotspots(projectId, scope, sort, limit);
    }

    @GetMapping("/metrics/modules")
    public ModuleMetricList modules(@PathVariable long projectId,
                                    @RequestParam(defaultValue = "PACKAGE") MetricLevel level) {
        return metrics.modules(projectId, level);
    }
}
