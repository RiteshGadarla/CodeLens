package com.codelens.api.dto;

import com.codelens.domain.SourceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// LOCAL needs path, GIT needs url; archives go to /upload
public record CreateProjectRequest(
        @Size(max = 200) String name,
        @NotNull SourceType sourceType,
        String path,
        String url,
        String branch,
        Boolean analyze
) {
}
