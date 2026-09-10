package com.codelens.repository;

import com.codelens.domain.SourceFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SourceFileRepository extends JpaRepository<SourceFile, Long> {

    interface FileStats {
        Long getFiles();

        Long getLoc();

        Long getParseErrors();
    }

    List<SourceFile> findByProjectId(Long projectId);

    long countByProjectId(Long projectId);

    @Query("""
            select count(f) as files, coalesce(sum(f.loc), 0) as loc,
                   coalesce(sum(case when f.parseError is null then 0 else 1 end), 0) as parseErrors
            from SourceFile f where f.projectId = :projectId""")
    FileStats stats(Long projectId);
}
