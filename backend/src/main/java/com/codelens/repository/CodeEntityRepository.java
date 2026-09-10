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

    // pattern/prefix are pre-escaped with '!'
    @Query("""
            select e from CodeEntity e
            where e.projectId = :projectId
              and e.kind in :kinds
              and (lower(e.name) like :pattern escape '!' or lower(e.qualifiedName) like :pattern escape '!')
            order by case when lower(e.name) = :exact then 0
                          when lower(e.name) like :prefix escape '!' then 1
                          else 2 end,
                     length(e.name), e.name
            """)
    List<CodeEntity> search(Long projectId, Collection<EntityKind> kinds, String pattern, String exact, String prefix,
                            Pageable page);
}
