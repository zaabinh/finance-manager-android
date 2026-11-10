package com.fintrack.api.controller;

import com.fintrack.api.dto.request.budget.MonthlyBudgetRequest;
import com.fintrack.api.dto.response.budget.MonthlyBudgetResponse;
import com.fintrack.api.security.AuthenticatedUser;
import com.fintrack.api.service.budget.MonthlyBudgetService;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!test")
@RequestMapping("/api/v1/monthly-budgets")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Budgets")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class MonthlyBudgetController {
    private final MonthlyBudgetService monthlies;
    public MonthlyBudgetController(MonthlyBudgetService monthlies) { this.monthlies = monthlies; }
    @GetMapping
    public List<MonthlyBudgetResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String month, @RequestParam(defaultValue = "ACTIVE") String status) {
        return monthlies.list(user.id(), month, status);
    }
    @GetMapping("/{id}")
    public MonthlyBudgetResponse detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return monthlies.detail(user.id(), id);
    }
    @PostMapping
    public ResponseEntity<MonthlyBudgetResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody MonthlyBudgetRequest request) {
        var result = monthlies.create(user.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/monthly-budgets/" + result.monthlyBudgetId())).body(result);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        monthlies.delete(user.id(), id); return ResponseEntity.noContent().build();
    }
}
