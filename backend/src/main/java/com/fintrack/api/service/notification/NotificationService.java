package com.fintrack.api.service.notification;

import com.fintrack.api.dto.response.notification.NotificationResponse;
import com.fintrack.api.repository.NotificationRepository;
import com.fintrack.api.service.finance.FinanceSupport;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test")
public class NotificationService {
    private final NotificationRepository notifications;
    public NotificationService(NotificationRepository notifications) { this.notifications=notifications; }
    public record Page(List<NotificationResponse> items,int page,long totalPages,long unreadCount,Instant snapshotAt) {}
    public record Count(long unreadCount,Instant snapshotAt) {}
    public record ReadAll(int affectedCount,long unreadCount,Instant snapshotAt) {}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page list(UUID user,int page,int size,String status) {
        if(page<0||size<1||size>100||!Set.of("ALL","READ","UNREAD").contains(status)) throw FinanceSupport.badRequest("Invalid notification query");
        // This first SELECT establishes the repeatable-read snapshot and its cutoff together.
        Instant snapshot=notifications.now(); long count=notifications.count(user,status);
        return new Page(notifications.list(user,page,size,status),page,(count+size-1)/size,notifications.unread(user),snapshot);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Count count(UUID user) { Instant snapshot=notifications.now(); return new Count(notifications.unread(user),snapshot); }
    @Transactional(readOnly=true)
    public NotificationResponse detail(UUID user,UUID id) { return notifications.detail(user,id); }
    @Transactional
    public NotificationResponse read(UUID user,UUID id) { notifications.lockInbox(user); notifications.read(user,id); return notifications.detail(user,id); }
    @Transactional
    public ReadAll readAll(UUID user,Instant through) {
        notifications.lockInbox(user);
        if(through==null||through.isAfter(notifications.now())) throw FinanceSupport.badRequest("through must be a previous UTC snapshotAt");
        int affected=notifications.readAll(user,through); return new ReadAll(affected,notifications.unread(user),notifications.now());
    }
    @Transactional
    public void delete(UUID user,UUID id) { notifications.lockInbox(user); notifications.delete(user,id); }
}
