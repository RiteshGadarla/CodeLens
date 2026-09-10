package com.codelens.api.dto;

import com.codelens.domain.AnalysisRun;
import com.codelens.domain.RunMode;
import com.codelens.domain.RunStatus;

import java.time.Instant;

public record RunDto(long id, RunMode mode, RunStatus status, String commitHash, int filesTotal, int filesParsed,
                     int filesDeleted, int entities, int edges, Long durationMs, String error,
                     Instant startedAt, Instant finishedAt) {

    public static RunDto of(AnalysisRun r) {
        return new RunDto(r.getId(), r.getMode(), r.getStatus(), r.getCommitHash(), r.getFilesTotal(),
                r.getFilesParsed(), r.getFilesDeleted(), r.getEntities(), r.getEdges(), r.getDurationMs(),
                r.getError(), r.getStartedAt(), r.getFinishedAt());
    }
}
