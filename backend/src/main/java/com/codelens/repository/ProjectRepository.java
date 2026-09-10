package com.codelens.repository;

import com.codelens.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    boolean existsByIdAndOwnerId(Long id, Long ownerId);

    @Modifying
    @Query("update Project p set p.ownerId = :ownerId where p.ownerId is null")
    int adoptUnowned(Long ownerId);
}
