package com.codelens.api;

import com.codelens.api.dto.DashboardDto;
import com.codelens.auth.CurrentUser;
import com.codelens.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Dashboard", description = "KPIs across your projects")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/api/dashboard")
    @Operation(summary = "Totals, per-project KPIs, run activity and riskiest types")
    public DashboardDto dashboard() {
        return dashboard.forOwner(CurrentUser.id());
    }
}
