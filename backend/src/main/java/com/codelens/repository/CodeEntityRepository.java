package com.codelens.repository;

import com.codelens.domain.CodeEntity;
import com.codelens.domain.EntityKind;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CodeEntityRepository extends JpaRepository<CodeEntity, Long> {

    List<CodeEntity> findByProjectId(Long projectId);

    List<CodeEntity> findByFileIdIn(Collection<Long> fileIds);

    List<CodeEntity> findByParentIdOrderByStartLine(Long parentId);

    Optional<CodeEntity> findByProjectIdAndQualifiedName(Long projectId, String qualifiedName);

    long countByProjectId(Long projectId);

    @Query("""
            select e from CodeEntity e
            where e.projectId = :projectId
              and e.kind in :kinds
              and (lower(e.name) like lower(concat('%', :q, '%'))
                   or lower(e.qualifiedName) like lower(concat('%', :q, '%')))
            order by length(e.name), e.name
            """)
    List<CodeEntity> search(Long projectId, String q, Collection<EntityKind> kinds, Pageable page);
}
