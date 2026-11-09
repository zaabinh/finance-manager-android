package com.fintrack.api.service.notification;

import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Service
@Profile("!test")
public class NotificationOutboxWorker {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final FinanceNotificationProducer producer;
    private final EmailTemplateRenderer renderer;
    private final EmailSenderService sender;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final String origin;
    public NotificationOutboxWorker(JdbcTemplate jdbc,PlatformTransactionManager manager,FinanceNotificationProducer producer,
            EmailTemplateRenderer renderer,EmailSenderService sender,Clock clock,MeterRegistry metrics,
            @Value("${app.notifications.action-origin:}") String origin) {
        this.jdbc=jdbc;tx=new TransactionTemplate(manager);this.producer=producer;this.renderer=renderer;this.sender=sender;this.clock=clock;this.metrics=metrics;
        this.origin=origin.isBlank()?"":FinanceEmailContent.origin(origin);
    }
    // Records containing recipient/body are deliberately not exposed as generated record.toString().
    static class Claim {
        UUID id,token,user; String type,recipient,subject,template,variables,html; Map<String,Object> payload;
        int attempts; Instant expires,first;
    }
    public void drain(int batch) {
        if(TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Mail worker cannot join a financial transaction");
        if(origin.isEmpty())return;
        for(int i=0;i<batch;i++) {
            Claim claim=claim(); if(claim==null)return;
            try {
                String html=prepare(claim);
                if(html==null)continue;
                // Recheck fencing immediately before network; provider timeout is 15s, lease is 90s.
                if(!owns(claim))continue;
                sender.send(claim.recipient,claim.subject,html,claim.id);
                finish(claim,"SENT","ACCEPTED",null);
            } catch(RuntimeException error) {
                Failure failure=classify(error,clock.instant());
                Instant next=clock.instant().plusSeconds(backoff(claim.attempts));
                if(failure.retryAfter!=null&&failure.retryAfter.isAfter(next))next=failure.retryAfter;
                boolean retry=failure.transientFailure&&claim.attempts<5&&next.isBefore(claim.expires)&&next.isBefore(claim.first.plus(Duration.ofHours(23)));
                finish(claim,retry?"RETRY":"DEAD",failure.reason,retry?next:null);
            }
        }
    }
    Claim claim() {
        return tx.execute(status->{
            var rows=jdbc.queryForList("""
                SELECT o.*,n.type,n.payload FROM fintrack.notification_outbox o JOIN fintrack.notifications n ON n.id=o.notification_id
                WHERE ((o.state IN ('PENDING','RETRY') AND o.next_attempt_at<=?) OR (o.state='IN_FLIGHT' AND o.lease_until<=?))
                ORDER BY o.next_attempt_at,o.delivery_id FOR UPDATE OF o SKIP LOCKED LIMIT 1
                """,Timestamp.from(clock.instant()),Timestamp.from(clock.instant()));
            if(rows.isEmpty())return null;
            var r=rows.getFirst(); var c=new Claim(); c.id=(UUID)r.get("delivery_id");c.user=(UUID)r.get("user_id");c.token=UUID.randomUUID();
            c.recipient=(String)r.get("recipient");c.subject=(String)r.get("subject");c.template=(String)r.get("template_name");c.variables=r.get("variables").toString();c.html=(String)r.get("rendered_html");
            c.type=(String)r.get("type");c.payload=NotificationJson.read(r.get("payload").toString());
            c.attempts=((Number)r.get("attempts")).intValue()+1;c.expires=((Timestamp)r.get("expires_at")).toInstant();c.first=r.get("first_attempt_at")==null?clock.instant():((Timestamp)r.get("first_attempt_at")).toInstant();
            jdbc.update("UPDATE fintrack.notification_outbox SET state='IN_FLIGHT',attempts=?,claim_token=?,lease_until=?,first_attempt_at=COALESCE(first_attempt_at,?) WHERE delivery_id=?",c.attempts,c.token,Timestamp.from(clock.instant().plusSeconds(90)),Timestamp.from(c.first),c.id);
            return c;
        });
    }
    private String prepare(Claim c) {
        return tx.execute(status->{
            var rows=jdbc.queryForList("SELECT o.state,o.claim_token,o.lease_until,n.deleted_at FROM fintrack.notification_outbox o JOIN fintrack.notifications n ON n.id=o.notification_id WHERE o.delivery_id=? FOR UPDATE OF o",c.id);
            if(rows.isEmpty()||!c.token.equals(rows.getFirst().get("claim_token"))||!"IN_FLIGHT".equals(rows.getFirst().get("state")))return null;
            var user=producer.user(c.user);
            boolean expired=!clock.instant().isBefore(c.expires)||!clock.instant().isBefore(c.first.plus(Duration.ofHours(23)));
            boolean stale=rows.getFirst().get("deleted_at")!=null||user==null||!user.active()||!user.verified()||!user.emailEnabled()||!Objects.equals(c.recipient,user.email())||!producer.fresh(c.user,c.type,c.payload);
            if(expired||stale||c.attempts>5) { finish(c,"CANCELLED",expired?"EXPIRED":c.attempts>5?"ATTEMPTS_EXHAUSTED":"STALE",null);return null; }
            Map<String,String> vars=NotificationJson.strings(c.variables); FinanceEmailContent.validate(c.template,vars,origin);
            if(!vars.get("title").equals(c.subject))throw new IllegalArgumentException("Template subject differs from immutable payload");
            String html=c.html==null?renderer.render(c.template,vars):c.html;
            if(c.html==null) jdbc.update("UPDATE fintrack.notification_outbox SET rendered_html=? WHERE delivery_id=? AND claim_token=?",html,c.id,c.token);
            return html;
        });
    }
    private boolean owns(Claim c) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT state='IN_FLIGHT' AND claim_token=? AND lease_until>? FROM fintrack.notification_outbox WHERE delivery_id=?",Boolean.class,c.token,Timestamp.from(clock.instant()),c.id));
    }
    void finish(Claim c,String state,String reason,Instant next) {
        int changed=jdbc.update("""
            UPDATE fintrack.notification_outbox SET state=?,reason=?,next_attempt_at=COALESCE(?,next_attempt_at),
              sent_at=CASE WHEN ?='SENT' THEN ? ELSE sent_at END,claim_token=NULL,lease_until=NULL
            WHERE delivery_id=? AND state='IN_FLIGHT' AND claim_token=? AND lease_until>?
            """,state,reason,next==null?null:Timestamp.from(next),state,Timestamp.from(clock.instant()),c.id,c.token,Timestamp.from(clock.instant()));
        if(changed>0)metrics.counter("fintrack.notification.delivery","state",state,"reason",reason).increment();
    }
    static long backoff(int attempts) { return switch(attempts) {case 1->60;case 2->300;case 3->900;default->3600;}+ThreadLocalRandom.current().nextLong(16); }
    record Failure(boolean transientFailure,String reason,Instant retryAfter) {}
    static Failure classify(RuntimeException error,Instant now) {
        if(error instanceof ResourceAccessException) return new Failure(true,"NETWORK",null);
        if(error instanceof RestClientResponseException response) {
            int code=response.getStatusCode().value();
            boolean concurrent=code==409&&response.getResponseBodyAsString().contains("concurrent_idempotent_requests");
            boolean retry=code==429||code>=500||concurrent;
            Instant after=null;
            String raw=response.getResponseHeaders()==null?null:response.getResponseHeaders().getFirst("Retry-After");
            try { if(raw!=null) { after=raw.matches("[0-9]{1,7}")?now.plusSeconds(Long.parseLong(raw)):ZonedDateTime.parse(raw,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(); if(after.isBefore(now))after=null; } }
            catch(RuntimeException ignored) { /* Invalid provider header falls back to bounded backoff. */ }
            return new Failure(retry,retry?"PROVIDER_TRANSIENT":"PROVIDER_PERMANENT",after);
        }
        return new Failure(false,"CONFIG_OR_TEMPLATE",null);
    }
}
