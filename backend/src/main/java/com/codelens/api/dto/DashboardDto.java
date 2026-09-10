package com.codelens.api.dto;

import com.codelens.domain.EntityKind;
import com.codelens.domain.ProjectStatus;
import com.codelens.domain.RunStatus;
import com.codelens.domain.SourceType;
import com.codelens.domain.Stereotype;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// portfolio view across the caller's projects
public record DashboardDto(Totals totals, List<ProjectKpi> projects, List<DayActivity> activity,
                           List<PortfolioHotspot> hotspots) {

    public record Totals(int projects, int ready, int analyzing, int failed, long files, long loc, long types,
                         long methods, long endpoints, long tests, long edges, long high, long medium, long low,
                         int runs, int failedRuns, Long avgRunMs) {
    }

    public record ProjectKpi(long id, String name, SourceType sourceType, String sourceUri, ProjectStatus status,
                             boolean analyzing, long files, long loc, long types, long methods, long endpoints,
                             long tests, long edges, long high, long medium, long low, double avgRisk, double maxRisk,
                             RunStatus lastRunStatus, Instant lastRunAt, Long lastRunMs) {
    }

    // utc days
    public record DayActivity(LocalDate day, int success, int failed, Long avgMs) {
    }

    public record PortfolioHotspot(long projectId, String projectName, long entityId, String label, EntityKind kind,
                                   Stereotype role, double riskScore, int dependents) {
    }
}
