package com.codelens.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "dependency_edge")
@Getter
@Setter
@NoArgsConstructor
public class DependencyEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EdgeType type;

    private int weight = 1;

    // file the edge was found in (for incremental cleanup)
    @Column(nullable = false)
    private Long fileId;
}
