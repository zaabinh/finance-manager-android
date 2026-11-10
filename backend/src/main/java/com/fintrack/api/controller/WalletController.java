package com.fintrack.api.controller;

import com.fintrack.api.dto.request.wallet.WalletRequest;
import com.fintrack.api.dto.request.wallet.WalletStatusRequest;
import com.fintrack.api.dto.response.wallet.WalletResponse;
import com.fintrack.api.security.AuthenticatedUser;
import com.fintrack.api.service.wallet.WalletService;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!test")
@RequestMapping("/api/v1/wallets")
@io.swagger.v3.oas.annotations.tags.Tag(name="Wallets")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
public class WalletController {
    private final WalletService wallets;
    public WalletController(WalletService wallets) {this.wallets=wallets;}
    @GetMapping public List<WalletResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue="ALL") String status) {return wallets.list(user.id(),status);}
    @GetMapping("/{id}") public WalletResponse get(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id) {return wallets.get(user.id(),id);}
    @PostMapping public ResponseEntity<WalletResponse> create(@AuthenticationPrincipal AuthenticatedUser user,@RequestBody WalletRequest body) {
        WalletResponse wallet=wallets.create(user.id(),body);
        return ResponseEntity.created(URI.create("/api/v1/wallets/"+wallet.walletId())).body(wallet);
    }
    @PutMapping("/{id}") public WalletResponse update(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id,@RequestBody WalletRequest body) {return wallets.update(user.id(),id,body);}
    @PatchMapping("/{id}/status") public WalletResponse status(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id,@RequestBody WalletStatusRequest body) {return wallets.status(user.id(),id,body);}
    @DeleteMapping("/{id}") public ResponseEntity<Void> archive(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id) {wallets.archive(user.id(),id);return ResponseEntity.noContent().build();}
}
