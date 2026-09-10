package com.codelens.repository;

import com.codelens.domain.SourceFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceFileRepository extends JpaRepository<SourceFile, Long> {

    List<SourceFile> findByProjectId(Long projectId);

    long countByProjectId(Long projectId);
}
