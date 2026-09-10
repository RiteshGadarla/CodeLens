package com.codelens.api.dto;

import com.codelens.domain.AnalysisRun;
import com.codelens.domain.Project;
import com.codelens.domain.ProjectStatus;
import com.codelens.domain.SourceType;

import java.time.Instant;

public record ProjectDto(long id, String name, SourceType sourceType, String sourceUri, String branch,
                         String lastCommit, ProjectStatus status, String statusMessage, boolean analyzing,
                         Instant createdAt, Instant updatedAt, RunDto latestRun) {

    public static ProjectDto of(Project p, AnalysisRun latest, boolean analyzing) {
        return new ProjectDto(p.getId(), p.getName(), p.getSourceType(), p.getSourceUri(), p.getBranch(),
                p.getLastCommit(), p.getStatus(), p.getStatusMessage(), analyzing, p.getCreatedAt(), p.getUpdatedAt(),
                latest == null ? null : RunDto.of(latest));
    }
}
