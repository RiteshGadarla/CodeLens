package com.codelens.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "source_file")
@Getter
@Setter
@NoArgsConstructor
public class SourceFile {

    @Id
    // assigned by GraphStore
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, columnDefinition = "text")
    private String path;

    @Column(nullable = false)
    private String module;

    private String packageName;

    @Column(nullable = false)
    private String sha256;

    private int loc;
    private boolean test;

    @Column(columnDefinition = "text")
    private String parseError;
}
