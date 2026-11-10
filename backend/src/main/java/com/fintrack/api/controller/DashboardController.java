package com.fintrack.api.controller;

import com.fintrack.api.dto.response.dashboard.DashboardResponse;
import com.fintrack.api.security.AuthenticatedUser;
import com.fintrack.api.service.dashboard.DashboardService;
import com.fintrack.api.service.finance.FinanceSupport;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!test")
@RequestMapping("/api/v1/dashboard")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Dashboard")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class DashboardController {
    private final DashboardService dashboard;
    public DashboardController(DashboardService dashboard) { this.dashboard = dashboard; }

    @GetMapping
    @io.swagger.v3.oas.annotations.Operation(summary = "Current assets and selected-month activity in one read-only snapshot",
            description = "Only one optional month=YYYY-MM (0001–9998) query is accepted; default is the user's local current month. "
                    + "Recent transactions are limited to five. Notifications remain unavailable on this independent branch.",
            parameters = @io.swagger.v3.oas.annotations.Parameter(name = "month", example = "2026-11",
                    in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", pattern = "[0-9]{4}-[0-9]{2}")))
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "DashboardResponse (direct JSON)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid, duplicate or unknown query"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "FINANCE_AMOUNT_OVERFLOW")})
    public DashboardResponse get(@AuthenticationPrincipal AuthenticatedUser user,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestParam MultiValueMap<String, String> query) {
        if (query.keySet().stream().anyMatch(key -> !"month".equals(key))
                || (query.containsKey("month") && query.get("month").size() != 1))
            throw FinanceSupport.badRequest("Only one optional month query is supported");
        return dashboard.get(user.id(), query.getFirst("month"));
    }
}
