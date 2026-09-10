package com.codelens.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "analysis_run")
@Getter
@Setter
@NoArgsConstructor
public class AnalysisRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status = RunStatus.RUNNING;

    private String commitHash;

    private int filesTotal;
    private int filesParsed;
    private int filesDeleted;
    private int entities;
    private int edges;
    private Long durationMs;

    @Column(columnDefinition = "text")
    private String error;

    @Column(nullable = false, columnDefinition = "timestamptz")
    private Instant startedAt;

    @Column(columnDefinition = "timestamptz")
    private Instant finishedAt;

    public AnalysisRun(Long projectId, RunMode mode) {
        this.projectId = projectId;
        this.mode = mode;
        this.startedAt = Instant.now();
    }
}
