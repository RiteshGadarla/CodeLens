package com.codelens.repository;

import com.codelens.domain.EntityKind;
import com.codelens.domain.EntityMetric;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface EntityMetricRepository extends JpaRepository<EntityMetric, Long> {

    interface ScaleView {
        Integer getDependents();

        Integer getDepth();

        Integer getCoupling();

        Integer getComplexity();
    }

    List<EntityMetric> findByProjectId(Long projectId);

    List<EntityMetric> findByProjectIdOrderByRiskScoreDesc(Long projectId, Pageable page);

    // sorted via the pageable
    @Query("select m from EntityMetric m, CodeEntity e where e.id = m.entityId and m.projectId = :projectId and e.kind in :kinds")
    List<EntityMetric> hotspots(Long projectId, Collection<EntityKind> kinds, Pageable page);

    @Query("""
            select max(m.dependents) as dependents, max(m.depth) as depth,
                   max(m.fanIn + m.fanOut) as coupling, max(m.complexity) as complexity
            from EntityMetric m where m.projectId = :projectId""")
    ScaleView scale(Long projectId);
}
