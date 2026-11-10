package com.fintrack.api.controller;

import com.fintrack.api.dto.response.notification.NotificationResponse;
import com.fintrack.api.security.AuthenticatedUser;
import com.fintrack.api.service.finance.FinanceRequest;
import com.fintrack.api.service.finance.FinanceBooleanDeserializer;
import com.fintrack.api.service.finance.FinanceSupport;
import com.fintrack.api.service.notification.NotificationService;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!test")
@RequestMapping("/api/v1/notifications")
@io.swagger.v3.oas.annotations.tags.Tag(name="Notifications")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications) { this.notifications=notifications; }
    public static class ReadRequest extends FinanceRequest { @JsonDeserialize(using=FinanceBooleanDeserializer.class) public Boolean read; }
    public static class ReadAllRequest extends FinanceRequest { public String through; }
    @GetMapping public NotificationService.Page list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="ALL") String readStatus,@RequestParam MultiValueMap<String,String> query) {
        if(!Set.of("page","size","readStatus").containsAll(query.keySet())||query.values().stream().anyMatch(v->v.size()!=1)) throw FinanceSupport.badRequest("Unsupported notification query");
        return notifications.list(user.id(),page,size,readStatus);
    }
    @GetMapping("/unread-count") public NotificationService.Count count(@AuthenticationPrincipal AuthenticatedUser user) { return notifications.count(user.id()); }
    @GetMapping("/{id}") public NotificationResponse detail(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id) { return notifications.detail(user.id(),id); }
    @PatchMapping("/{id}/read") public NotificationResponse read(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id,@RequestBody ReadRequest body) {
        if(!Boolean.TRUE.equals(body.read)) throw FinanceSupport.badRequest("Only read=true is supported");
        return notifications.read(user.id(),id);
    }
    @PostMapping("/read-all") public NotificationService.ReadAll readAll(@AuthenticationPrincipal AuthenticatedUser user,@RequestBody ReadAllRequest body) {
        Instant through;
        try { if(body.through==null||!body.through.endsWith("Z")) throw new IllegalArgumentException(); through=Instant.parse(body.through); }
        catch(RuntimeException error) { throw FinanceSupport.badRequest("through must be a UTC timestamp"); }
        return notifications.readAll(user.id(),through);
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id) { notifications.delete(user.id(),id); return ResponseEntity.noContent().build(); }
}
