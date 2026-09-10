package com.codelens.auth;

import com.codelens.common.NotFoundException;
import com.codelens.repository.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

// runs before controllers, so cached project data is never served to other accounts
@Component
public class ProjectAccessInterceptor implements HandlerInterceptor {

    private final ProjectRepository projects;

    public ProjectAccessInterceptor(ProjectRepository projects) {
        this.projects = projects;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        @SuppressWarnings("unchecked")
        var vars = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String raw = vars == null ? null : vars.getOrDefault("projectId", vars.get("id"));
        if (raw == null) return true;
        long id;
        try {
            id = Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return true; // binding rejects it with 400
        }
        // 404, not 403: other accounts' ids stay hidden
        if (!projects.existsByIdAndOwnerId(id, CurrentUser.id())) throw new NotFoundException("project", id);
        return true;
    }
}
