package com.codelens.repository;

import com.codelens.domain.DependencyEdge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DependencyEdgeRepository extends JpaRepository<DependencyEdge, Long> {

    List<DependencyEdge> findByProjectId(Long projectId);

    long countByProjectId(Long projectId);
}
