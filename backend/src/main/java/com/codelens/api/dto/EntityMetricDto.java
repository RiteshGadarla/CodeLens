package com.codelens.api.dto;

import com.codelens.domain.EntityMetric;
import com.codelens.metrics.RiskScorer;

public record EntityMetricDto(int fanIn, int fanOut, int dependents, int dependencies, int depth, int complexity,
                              double riskScore, String riskLevel, boolean exposed) {

    public static EntityMetricDto of(EntityMetric m) {
        return new EntityMetricDto(m.getFanIn(), m.getFanOut(), m.getDependents(), m.getDependencies(), m.getDepth(),
                m.getComplexity(), m.getRiskScore(), RiskScorer.level(m.getRiskScore()), m.isExposed());
    }
}
