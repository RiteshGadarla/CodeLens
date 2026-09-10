package com.codelens.repository;

import com.codelens.domain.AnalysisRun;
import com.codelens.domain.RunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, Long> {

    List<AnalysisRun> findTop20ByProjectIdOrderByStartedAtDesc(Long projectId);

    Optional<AnalysisRun> findFirstByProjectIdOrderByStartedAtDesc(Long projectId);

    Optional<AnalysisRun> findFirstByProjectIdAndStatusOrderByStartedAtDesc(Long projectId, RunStatus status);
}
