package com.fintrack.api.controller;

import com.fintrack.api.dto.request.transaction.TransactionRequest;
import com.fintrack.api.dto.response.transaction.TransactionPageResponse;
import com.fintrack.api.dto.response.transaction.TransactionResponse;
import com.fintrack.api.security.AuthenticatedUser;
import com.fintrack.api.service.transaction.TransactionService;
import java.net.URI;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!test")
@RequestMapping("/api/v1/transactions")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Transactions")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class TransactionController {
    private final TransactionService transactions;
    public TransactionController(TransactionService transactions) { this.transactions = transactions; }

    @GetMapping
    public TransactionPageResponse list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String dateFrom, @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) UUID walletId, @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String type, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "occurredAt,desc") String sort) {
        return transactions.list(user.id(), page, size, dateFrom, dateTo, walletId, categoryId, type, q, sort);
    }
    @GetMapping("/{id}")
    public TransactionResponse detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return transactions.detail(user.id(), id);
    }
    @PostMapping
    public ResponseEntity<TransactionResponse> create(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody TransactionRequest body) {
        var result = transactions.create(user.id(), body);
        if (!result.created()) return ResponseEntity.ok(result.body());
        return ResponseEntity.created(URI.create("/api/v1/transactions/" + result.body().transactionId())).body(result.body());
    }
    @PutMapping("/{id}")
    public TransactionResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @RequestBody TransactionRequest body) {
        return transactions.update(user.id(), id, body);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        transactions.delete(user.id(), id); return ResponseEntity.noContent().build();
    }
}
