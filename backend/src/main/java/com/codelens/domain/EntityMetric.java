package com.codelens.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "entity_metric")
@Getter
@Setter
@NoArgsConstructor
public class EntityMetric {

    @Id
    private Long entityId;

    @Column(nullable = false)
    private Long projectId;

    private int fanIn;
    private int fanOut;
    private int dependents;
    private int dependencies;
    private int depth;
    private int complexity;
    private double riskScore;
}
