package com.codelens.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "code_entity")
@Getter
@Setter
@NoArgsConstructor
public class CodeEntity {

    @Id
    // assigned by GraphStore
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long fileId;

    private Long parentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntityKind kind;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String qualifiedName;

    private String packageName;

    @Column(columnDefinition = "text")
    private String signature;

    private String visibility;
    private String modifiers;

    @Column(columnDefinition = "text")
    private String annotations;

    @Enumerated(EnumType.STRING)
    private Stereotype stereotype;

    private String httpMethod;

    @Column(columnDefinition = "text")
    private String httpPath;

    private Integer startLine;
    private Integer endLine;

    private int complexity = 1;
}
