package com.codelens.api.dto;

import com.codelens.graph.EntityRef;
import com.codelens.metrics.ModuleMetrics;

import java.util.List;
import java.util.Map;

public record OverviewDto(Stats stats, Map<String, Integer> kinds, Map<String, Integer> roles, RiskDistribution risk,
                          Kpis kpis, List<HotspotDto> topRisks, List<ModuleMetrics> modules,
                          List<List<EntityRef>> cycles) {

    public record Stats(long files, long loc, long parseErrors, int types, int methods, int endpoints, int tests,
                        int edges, int modules, int packages) {
    }

    public record RiskDistribution(int low, int medium, int high) {
    }

    // risk over types and methods; complexity over methods
    public record Kpis(double avgRisk, double maxRisk, double avgComplexity, int maxComplexity, int maxDepth,
                       int exposed, List<Bucket> complexity) {
    }

    public record Bucket(String label, int count) {
    }
}
