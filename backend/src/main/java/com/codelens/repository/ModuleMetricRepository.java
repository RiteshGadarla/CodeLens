package com.codelens.repository;

import com.codelens.domain.MetricLevel;
import com.codelens.domain.ModuleMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModuleMetricRepository extends JpaRepository<ModuleMetric, Long> {

    List<ModuleMetric> findByProjectIdAndLevelOrderByInstabilityDesc(Long projectId, MetricLevel level);
}
