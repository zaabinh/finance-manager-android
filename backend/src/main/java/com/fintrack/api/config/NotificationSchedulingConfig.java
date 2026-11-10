package com.fintrack.api.config;

import com.fintrack.api.service.notification.FinanceNotificationProducer;
import com.fintrack.api.service.notification.NotificationOutboxWorker;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@Profile("!test")
@EnableScheduling
public class NotificationSchedulingConfig {
    private final FinanceNotificationProducer producer;
    private final NotificationOutboxWorker worker;
    private final MeterRegistry metrics;
    private final boolean enabled,email;
    public NotificationSchedulingConfig(FinanceNotificationProducer producer,NotificationOutboxWorker worker,MeterRegistry metrics,
            @Value("${app.notifications.producers-enabled:false}") boolean enabled,@Value("${app.notifications.email-enabled:false}") boolean email) {
        this.producer=producer;this.worker=worker;this.metrics=metrics;this.enabled=enabled;this.email=email;
    }
    @Scheduled(fixedDelayString="${app.notifications.poll-delay-ms:60000}") public void produce() {
        if(enabled)run(()->{producer.processEvents(100);producer.daily(100);},"produce");
    }
    @Scheduled(fixedDelayString="${app.notifications.reconcile-delay-ms:900000}") public void reconcile() { if(enabled)run(()->producer.reconcile(100),"reconcile"); }
    @Scheduled(fixedDelayString="${app.notifications.delivery-delay-ms:10000}") public void deliver() { if(email)run(()->worker.drain(20),"deliver"); }
    private void run(Runnable action,String operation) {
        try { action.run(); }
        catch(RuntimeException error) { metrics.counter("fintrack.notification.poll.failure","operation",operation).increment(); }
    }
}
