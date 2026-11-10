package com.fintrack.api.controller;

import com.fintrack.api.dto.request.budget.BudgetRequest;
import com.fintrack.api.dto.response.budget.BudgetResponse;
import com.fintrack.api.security.AuthenticatedUser;
import com.fintrack.api.service.budget.BudgetService;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!test")
@RequestMapping("/api/v1/budgets")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Budgets")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class BudgetController {
    private final BudgetService budgets;
    public BudgetController(BudgetService budgets) { this.budgets = budgets; }
    @GetMapping
    public List<BudgetResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String month, @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "ACTIVE") String status) {
        return budgets.list(user.id(), month, categoryId, status);
    }
    @GetMapping("/{id}")
    public BudgetResponse detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) { return budgets.detail(user.id(), id); }
    @PostMapping
    public ResponseEntity<BudgetResponse> create(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody BudgetRequest request) {
        var result = budgets.create(user.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/budgets/" + result.budgetId())).body(result);
    }
    @PutMapping("/{id}")
    public BudgetResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @RequestBody BudgetRequest request) {
        return budgets.update(user.id(), id, request);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        budgets.delete(user.id(), id); return ResponseEntity.noContent().build();
    }
}
