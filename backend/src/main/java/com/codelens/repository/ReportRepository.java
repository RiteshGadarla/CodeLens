package com.codelens.repository;

import com.codelens.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findTop20ByProjectIdOrderByCreatedAtDesc(Long projectId);
}
