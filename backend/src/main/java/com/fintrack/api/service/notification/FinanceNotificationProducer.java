package com.fintrack.api.service.notification;

import com.fintrack.api.dto.response.budget.BudgetResponse;
import com.fintrack.api.repository.BudgetRepository;
import com.fintrack.api.repository.NotificationRepository;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.MeterRegistry;

@Service
@Profile("!test")
public class FinanceNotificationProducer {
    private final JdbcTemplate jdbc;
    private final BudgetRepository budgets;
    private final NotificationRepository inbox;
    private final TransactionTemplate tx;
    private final TransactionTemplate evaluationTx;
    private final Clock clock;
    private final String origin;
    private final MeterRegistry metrics;
    public FinanceNotificationProducer(JdbcTemplate jdbc,BudgetRepository budgets,NotificationRepository inbox,
            PlatformTransactionManager manager,Clock clock,@Value("${app.notifications.action-origin:}") String origin,MeterRegistry metrics) {
        this.jdbc=jdbc;this.budgets=budgets;this.inbox=inbox;this.tx=new TransactionTemplate(manager);this.clock=clock;
        this.evaluationTx=new TransactionTemplate(manager);
        this.evaluationTx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.origin=origin.isBlank()?"":FinanceEmailContent.origin(origin);
        this.metrics=metrics;
    }
    record User(UUID id,String name,String email,boolean active,boolean verified,ZoneId zone,boolean master,
            boolean daily,LocalTime time,boolean warning,boolean exceeded,boolean emailEnabled) {
        boolean allows(String type) { return active&&master&&switch(type) { case "DAILY_REMINDER" -> daily; case "BUDGET_WARNING" -> warning; case "BUDGET_EXCEEDED" -> exceeded; default -> false; }; }
        String scheduleVersion() { return zone+"|"+time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))+"|"+(active?"ACTIVE":"INACTIVE")+"|"+master+"|"+daily; }
    }
    User user(UUID id) {
        return jdbc.query("""
            SELECT u.*,COALESCE(NULLIF(trim(p.display_name),''),u.username) AS greeting,
              COALESCE(p.time_zone,'Asia/Ho_Chi_Minh') AS zone,n.notifications_enabled,n.daily_reminder_enabled,n.daily_reminder_time,
              n.budget_alert_enabled,n.budget_exceed_alert_enabled,n.email_finance_enabled
            FROM fintrack.users u LEFT JOIN fintrack.user_preferences p ON p.user_id=u.id
            LEFT JOIN fintrack.notification_settings n ON n.user_id=u.id WHERE u.id=?
            """,(r,row)->new User(id,r.getString("greeting"),r.getString("email"),"ACTIVE".equals(r.getString("status")),r.getBoolean("email_verified"),ZoneId.of(r.getString("zone")),r.getBoolean("notifications_enabled"),r.getBoolean("daily_reminder_enabled"),r.getTime("daily_reminder_time")==null?LocalTime.of(20,0):r.getTime("daily_reminder_time").toLocalTime(),r.getBoolean("budget_alert_enabled"),r.getBoolean("budget_exceed_alert_enabled"),r.getBoolean("email_finance_enabled")),id).stream().findFirst().orElse(null);
    }
    public void processEvents(int batch) {
        var users=jdbc.queryForList("SELECT user_id FROM fintrack.notification_finance_events WHERE processed_at IS NULL GROUP BY user_id ORDER BY min(id) LIMIT ?",UUID.class,batch);
        for(UUID id:users) isolated(()->evaluate(id,false),"events");
    }
    /** Periodic repair does not reset an existing cycle, and baselines unknown pre-existing budgets. */
    public void reconcile(int batch) {
        var users=jdbc.queryForList("""
            SELECT b.user_id FROM fintrack.budgets b LEFT JOIN fintrack.budget_notification_state s ON s.user_id=b.user_id AND s.budget_id=b.id
            WHERE b.archived_at IS NULL GROUP BY b.user_id ORDER BY min(COALESCE(s.last_evaluated_at,'epoch')) LIMIT ?
            """,UUID.class,batch);
        for(UUID user:users) isolated(()->evaluate(user,true),"reconcile");
    }
    private void isolated(Runnable action,String operation) {
        try { action.run(); }
        catch(RuntimeException error) { metrics.counter("fintrack.notification.user.failure","operation",operation).increment(); }
    }
    public void evaluate(UUID id,boolean reconcile) {
        evaluationTx.executeWithoutResult(status->{
            Boolean lock=jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(hashtextextended(?,115))",Boolean.class,id.toString());
            if(!Boolean.TRUE.equals(lock)) return;
            var events=jdbc.queryForList("SELECT id,baseline_reset,budget_id FROM fintrack.notification_finance_events WHERE user_id=? AND processed_at IS NULL ORDER BY id",id);
            if(events.isEmpty()&&!reconcile) return;
            User user=user(id); if(user==null) return;
            boolean reset=events.stream().anyMatch(e->Boolean.TRUE.equals(e.get("baseline_reset")));
            Set<UUID> createdOrEdited=new HashSet<>(); for(var e:events) if(e.get("budget_id")!=null) createdOrEdited.add((UUID)e.get("budget_id"));
            YearMonth current=YearMonth.from(clock.instant().atZone(user.zone));
            for(var b:budgets.list(id,null,null,"ALL")) {
                boolean parentActive=Boolean.TRUE.equals(jdbc.queryForObject("SELECT archived_at IS NULL FROM fintrack.monthly_budgets WHERE user_id=? AND id=?",Boolean.class,id,b.monthlyBudgetId()));
                boolean eligible=b.archivedAt()==null&&parentActive&&b.month().equals(current.toString());
                String context=b.categoryId()+"/"+b.month();
                int severity=eligible?severity(b.status()):0;
                var old=jdbc.queryForList("SELECT * FROM fintrack.budget_notification_state WHERE user_id=? AND budget_id=? FOR UPDATE",id,b.budgetId());
                int previous=0,highest=0; long cycle=1;
                if(!old.isEmpty()) {
                    var s=old.getFirst(); previous=((Number)s.get("observed_severity")).intValue(); highest=((Number)s.get("highest_emitted_severity")).intValue(); cycle=((Number)s.get("cycle_number")).longValue();
                    if(!context.equals(s.get("context"))) { previous=0;highest=0;cycle++; }
                    else if(severity==0&&previous>0) { cycle++;highest=0; }
                }
                boolean baseline=reset||(old.isEmpty()&&!createdOrEdited.contains(b.budgetId()));
                if(severity>highest) {
                    if(!baseline&&eligible&&user.allows(severity==2?"BUDGET_EXCEEDED":"BUDGET_WARNING")) createBudget(user,b,context,cycle,severity);
                    // Disabled channels still consume the observed semantic level, avoiding delayed catch-up.
                    highest=severity;
                }
                jdbc.update("""
                    INSERT INTO fintrack.budget_notification_state(user_id,budget_id,context,observed_severity,cycle_number,highest_emitted_severity,last_evaluated_at)
                    VALUES(?,?,?,?,?,?,?) ON CONFLICT(user_id,budget_id) DO UPDATE SET context=EXCLUDED.context,observed_severity=EXCLUDED.observed_severity,
                    cycle_number=EXCLUDED.cycle_number,highest_emitted_severity=EXCLUDED.highest_emitted_severity,last_evaluated_at=EXCLUDED.last_evaluated_at
                    """,id,b.budgetId(),context,severity,cycle,highest,Timestamp.from(clock.instant()));
            }
            // Acknowledge only event IDs actually observed, including out-of-order commits.
            for(var e:events) jdbc.update("UPDATE fintrack.notification_finance_events SET processed_at=statement_timestamp() WHERE id=?",e.get("id"));
            cancelStaleForUser(user);
        });
    }
    static int severity(String status) { return switch(status) {case "EXCEEDED"->2;case "WARNING"->1;default->0;}; }
    private void createBudget(User user,BudgetResponse b,String context,long cycle,int severity) {
        String category=jdbc.queryForObject("SELECT name FROM fintrack.categories WHERE user_id=? AND id=?",String.class,user.id,b.categoryId());
        boolean exceeded=severity==2;
        var vars=FinanceEmailContent.budget(user.name,category,b,exceeded,origin);
        var payload=new HashMap<String,Object>(); payload.put("version",1); payload.put("target",Map.of("screen","BUDGET_DETAIL","budgetId",b.budgetId().toString(),"month",b.month()));
        payload.put("budgetId",b.budgetId().toString());payload.put("context",context);payload.put("cycle",cycle);payload.put("severity",severity);
        Instant end=YearMonth.parse(b.month()).plusMonths(1).atDay(1).atStartOfDay(user.zone).toInstant();
        Instant expiry=clock.instant().plus(Duration.ofHours(24)); if(end.isBefore(expiry)) expiry=end;
        emit(user,"budget/"+b.budgetId()+"/"+context+"/"+cycle+"/"+severity,exceeded?"BUDGET_EXCEEDED":"BUDGET_WARNING",vars,b.spentMinor(),payload,expiry);
    }
    private void emit(User user,String eventKey,String type,Map<String,String> vars,Long amount,Map<String,Object> payload,Instant expires) {
        UUID id=inbox.insert(user.id,eventKey,type,vars.get("title"),vars.get("preheader"),amount,payload);
        if(id==null||!user.emailEnabled||!user.verified||user.email==null||origin.isEmpty()) return;
        String template=switch(type){case "DAILY_REMINDER"->"daily-reminder";case "BUDGET_WARNING"->"budget-warning";default->"budget-exceeded";};
        FinanceEmailContent.validate(template,vars,origin);
        jdbc.update("INSERT INTO fintrack.notification_outbox(user_id,notification_id,template_name,variables,recipient,subject,expires_at,next_attempt_at) VALUES(?,?,?,?::jsonb,?,?,?,?)",
            user.id,id,template,NotificationJson.write(vars),user.email,vars.get("title"),Timestamp.from(expires),Timestamp.from(clock.instant()));
    }
    boolean fresh(UUID userId,String type,Map<String,Object> payload) {
        User user=user(userId);
        if(user==null||!user.allows(type)) return false;
        if(type.equals("DAILY_REMINDER")) return payload.get("localDate").equals(clock.instant().atZone(user.zone).toLocalDate().toString())&&payload.get("scheduleVersion").equals(user.scheduleVersion());
        UUID budget=UUID.fromString((String)payload.get("budgetId"));
        var states=jdbc.queryForList("SELECT * FROM fintrack.budget_notification_state WHERE user_id=? AND budget_id=?",userId,budget);
        if(states.isEmpty()) return false;
        var s=states.getFirst(); int required=((Number)payload.get("severity")).intValue();
        if(!s.get("context").equals(payload.get("context"))||((Number)s.get("cycle_number")).longValue()!=((Number)payload.get("cycle")).longValue()||((Number)s.get("highest_emitted_severity")).intValue()>required) return false;
        // Read live usage as committed changes may be ahead of the event poller.
        var b=budgets.detail(userId,budget);
        boolean parentActive=Boolean.TRUE.equals(jdbc.queryForObject("SELECT archived_at IS NULL FROM fintrack.monthly_budgets WHERE user_id=? AND id=?",Boolean.class,userId,b.monthlyBudgetId()));
        return b.archivedAt()==null&&parentActive&&b.month().equals(YearMonth.from(clock.instant().atZone(user.zone)).toString())
            &&payload.get("context").equals(b.categoryId()+"/"+b.month())&&severity(b.status())==required;
    }
    private void cancelStaleForUser(User user) {
        var rows=jdbc.queryForList("SELECT o.delivery_id,n.type,n.payload FROM fintrack.notification_outbox o JOIN fintrack.notifications n ON n.id=o.notification_id WHERE o.user_id=? AND o.state IN ('PENDING','RETRY')",user.id);
        for(var r:rows) if(!user.emailEnabled||!user.verified||!fresh(user.id,(String)r.get("type"),NotificationJson.read(r.get("payload").toString())))
            jdbc.update("UPDATE fintrack.notification_outbox SET state='CANCELLED',reason='STALE' WHERE delivery_id=? AND state IN ('PENDING','RETRY')",r.get("delivery_id"));
    }
    public static Instant due(LocalDate date,LocalTime time,ZoneId zone) {
        LocalDateTime local=date.atTime(time); var offsets=zone.getRules().getValidOffsets(local);
        if(offsets.isEmpty()) return zone.getRules().getTransition(local).getDateTimeAfter().atZone(zone).toInstant();
        return local.atOffset(offsets.getFirst()).toInstant();
    }
    private Instant initialDue(User user,LocalDate last) {
        LocalDate today=clock.instant().atZone(user.zone).toLocalDate(); Instant due=due(today,user.time,user.zone);
        if(last!=null&&!today.isAfter(last)) return due(last.plusDays(1),user.time,user.zone);
        return due.isBefore(clock.instant().minus(Duration.ofHours(2)))?due(today.plusDays(1),user.time,user.zone):due;
    }
    public void daily(int batch) {
        // Bounded repair detects settings/timezone/user changes without writes taking schedule locks.
        var changed=jdbc.queryForList("""
            SELECT u.id FROM fintrack.users u JOIN fintrack.notification_settings n ON n.user_id=u.id
            LEFT JOIN fintrack.user_preferences p ON p.user_id=u.id LEFT JOIN fintrack.daily_notification_schedule d ON d.user_id=u.id
            WHERE d.user_id IS NULL OR d.schedule_version <> concat(COALESCE(p.time_zone,'Asia/Ho_Chi_Minh'),'|',to_char(COALESCE(n.daily_reminder_time,'20:00'::time),'HH24:MI:SS'),'|',CASE WHEN u.status='ACTIVE' THEN 'ACTIVE' ELSE 'INACTIVE' END,'|',n.notifications_enabled::text,'|',n.daily_reminder_enabled::text)
            ORDER BY u.id LIMIT ?
            """,UUID.class,batch);
        for(UUID id:changed) isolated(()->tx.executeWithoutResult(s->{
            User u=user(id); if(u==null) return;
            jdbc.update("INSERT INTO fintrack.daily_notification_schedule(user_id,schedule_version,next_due_at) VALUES(?,?,?) ON CONFLICT DO NOTHING",id,u.scheduleVersion(),Timestamp.from(initialDue(u,null)));
            var row=jdbc.queryForList("SELECT * FROM fintrack.daily_notification_schedule WHERE user_id=? FOR UPDATE SKIP LOCKED",id);
            if(row.isEmpty())return;
            if(!u.scheduleVersion().equals(row.getFirst().get("schedule_version"))) {
                var date=(java.sql.Date)row.getFirst().get("last_processed_local_date");
                jdbc.update("UPDATE fintrack.daily_notification_schedule SET schedule_version=?,next_due_at=? WHERE user_id=?",u.scheduleVersion(),Timestamp.from(initialDue(u,date==null?null:date.toLocalDate())),id);
            }
        }),"daily-repair");
        var dueUsers=jdbc.queryForList("SELECT user_id FROM fintrack.daily_notification_schedule WHERE next_due_at<=? ORDER BY next_due_at,user_id LIMIT ?",UUID.class,Timestamp.from(clock.instant()),batch);
        for(UUID dueUser:dueUsers) isolated(()->tx.executeWithoutResult(s->{
                var rows=jdbc.queryForList("SELECT * FROM fintrack.daily_notification_schedule WHERE user_id=? AND next_due_at<=? FOR UPDATE SKIP LOCKED",dueUser,Timestamp.from(clock.instant()));
                if(rows.isEmpty())return;
                var row=rows.getFirst(); UUID id=(UUID)row.get("user_id"); User u=user(id); if(u==null)return;
                Instant scheduled=((Timestamp)row.get("next_due_at")).toInstant(); LocalDate date=scheduled.atZone(u.zone).toLocalDate();
                var previous=(java.sql.Date)row.get("last_processed_local_date"); LocalDate last=previous==null?null:previous.toLocalDate();
                boolean version=u.scheduleVersion().equals(row.get("schedule_version"));
                if(version&&u.allows("DAILY_REMINDER")&&(last==null||date.isAfter(last))&&date.equals(clock.instant().atZone(u.zone).toLocalDate())&&!scheduled.isBefore(clock.instant().minus(Duration.ofHours(2)))) {
                    var payload=Map.<String,Object>of("version",1,"target",Map.of("screen","TRANSACTION_CREATE","localDate",date.toString()),"localDate",date.toString(),"scheduleVersion",u.scheduleVersion());
                    emit(u,"daily/"+date,"DAILY_REMINDER",FinanceEmailContent.daily(u.name,date,u.time,u.zone.toString(),origin),null,payload,date.plusDays(1).atStartOfDay(u.zone).toInstant());
                }
                LocalDate today=clock.instant().atZone(u.zone).toLocalDate(); LocalDate processedDate=last!=null&&last.isAfter(date)?last:date;
                Instant next=version?due((today.isAfter(processedDate)?today:processedDate).plusDays(1),u.time,u.zone):initialDue(u,last);
                jdbc.update("UPDATE fintrack.daily_notification_schedule SET schedule_version=?,next_due_at=?,last_processed_local_date=? WHERE user_id=?",u.scheduleVersion(),Timestamp.from(next),version?processedDate:last,id);
            }),"daily-produce");
    }
}
