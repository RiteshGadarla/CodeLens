package com.codelens.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "module_metric")
@Getter
@Setter
@NoArgsConstructor
public class ModuleMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetricLevel level;

    @Column(nullable = false)
    private String name;

    private int entities;
    private int afferent;
    private int efferent;
    private double instability;
    private double abstractness;
    private double distance;
}
