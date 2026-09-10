package com.codelens.repository;

import com.codelens.domain.EntityMetric;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntityMetricRepository extends JpaRepository<EntityMetric, Long> {

    List<EntityMetric> findByProjectId(Long projectId);

    List<EntityMetric> findByProjectIdOrderByRiskScoreDesc(Long projectId, Pageable page);
}
