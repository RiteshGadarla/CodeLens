package com.codelens.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "project")
@Getter
@Setter
@NoArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceType sourceType;

    @Column(columnDefinition = "text")
    private String sourceUri;

    @Column(nullable = false, columnDefinition = "text")
    private String localPath;

    private String branch;
    private String lastCommit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status = ProjectStatus.PENDING;

    @Column(columnDefinition = "text")
    private String statusMessage;

    @Column(nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
