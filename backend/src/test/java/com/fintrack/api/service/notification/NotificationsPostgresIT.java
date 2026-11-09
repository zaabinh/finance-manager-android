package com.fintrack.api.service.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fintrack.api.dto.request.transaction.TransactionRequest;
import com.fintrack.api.dto.request.user.UpdateNotificationSettingsRequest;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.repository.*;
import com.fintrack.api.service.auth.RefreshTokenRotatingService;
import com.fintrack.api.service.finance.FinanceSupport;
import com.fintrack.api.service.transaction.TransactionService;
import com.fintrack.api.service.user.UserService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.containers.PostgreSQLContainer;

class NotificationsPostgresIT {
    static PostgreSQLContainer<?> postgres;
    static JdbcTemplate jdbc;
    static JdbcTransactionManager manager;
    static TransactionTemplate tx;
    MutableClock clock;
    FinanceNotificationProducer producer;
    NotificationRepository repository;
    NotificationService service;
    NotificationSettingsV2Service settings;
    TransactionService transactions;
    UUID user,other,wallet,category,budget,parent;
    String origin="https://finance.example.test";
    SimpleMeterRegistry metrics;
    @BeforeAll static void start() {
        String url=System.getenv("FINANCE_TEST_JDBC_URL"); DriverManagerDataSource ds;
        if(url!=null&&!url.isBlank()) ds=new DriverManagerDataSource(url,System.getenv("FINANCE_TEST_DB_USER"),System.getenv("FINANCE_TEST_DB_PASSWORD"));
        else { postgres=new PostgreSQLContainer<>("postgres:17.7");postgres.start();ds=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()); }
        Flyway.configure().dataSource(ds).defaultSchema("fintrack").load().migrate();
        jdbc=new JdbcTemplate(ds);manager=new JdbcTransactionManager(ds);tx=new TransactionTemplate(manager);
    }
    @AfterAll static void stop() { if(postgres!=null)postgres.stop(); }
    @BeforeEach void fixture() {
        jdbc.execute("TRUNCATE fintrack.users CASCADE");
        clock=new MutableClock(Instant.parse("2026-11-09T21:00:00Z"));metrics=new SimpleMeterRegistry();
        repository=new NotificationRepository(jdbc);service=new NotificationService(repository);
        producer=new FinanceNotificationProducer(jdbc,new BudgetRepository(jdbc),repository,manager,clock,origin,metrics);
        settings=new NotificationSettingsV2Service(jdbc,new NotificationSettingsRepository(jdbc));
        transactions=new TransactionService(new TransactionRepository(jdbc),new FinanceSupport(jdbc));
        user=user();other=user();wallet=UUID.randomUUID();category=category();parent=UUID.randomUUID();budget=UUID.randomUUID();
        jdbc.update("INSERT INTO fintrack.wallets(id,user_id,name,wallet_type,opening_balance_minor) VALUES(?,?,'Main','CASH',100000000)",wallet,user);
        producer.processEvents(100);
        jdbc.update("INSERT INTO fintrack.monthly_budgets(id,user_id,month) VALUES(?,?,'2026-11-01')",parent,user);
        jdbc.update("INSERT INTO fintrack.budgets(id,user_id,monthly_budget_id,category_id,period_start,period_end,limit_minor) VALUES(?,?,?,?,'2026-11-01','2026-12-01',100000)",budget,user,parent,category);
        producer.processEvents(100);
    }
    UUID user() {
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO fintrack.users(id,username,email,password_hash,email_verified) VALUES(?,?,?,'hash',true)",id,"u"+id,"u"+id+"@example.test");
        jdbc.update("INSERT INTO fintrack.user_preferences(user_id,time_zone) VALUES(?,'UTC')",id);
        jdbc.update("INSERT INTO fintrack.notification_settings(user_id,notifications_enabled,daily_reminder_enabled,daily_reminder_time,email_finance_enabled) VALUES(?,true,true,'21:00',true)",id);return id;
    }
    UUID category() { UUID id=UUID.randomUUID();jdbc.update("INSERT INTO fintrack.categories(id,user_id,name,category_type) VALUES(?,?,'Food','EXPENSE')",id,user);return id; }
    TransactionRequest request(long amount) { var r=new TransactionRequest();r.transactionType="EXPENSE";r.amountMinor=amount;r.categoryId=category;r.walletId=wallet;r.occurredAt="2026-11-09T12:00:00Z";r.clientRequestId=UUID.randomUUID();return r; }
    UUID expense(long amount) { return tx.execute(s->transactions.create(user,request(amount)).body().transactionId()); }
    long inboxCount() { return jdbc.queryForObject("SELECT count(*) FROM fintrack.notifications WHERE user_id=?",Long.class,user); }
    long outboxCount() { return jdbc.queryForObject("SELECT count(*) FROM fintrack.notification_outbox WHERE user_id=?",Long.class,user); }
    UUID insert(UUID owner,String key) { return tx.execute(s->repository.insert(owner,key,"DAILY_REMINDER","title","body",null,Map.of("version",1,"target",Map.of("screen","TRANSACTION_CREATE","localDate","2026-11-09")))); }
    NotificationOutboxWorker worker(EmailSenderService sender) { return new NotificationOutboxWorker(jdbc,manager,producer,new EmailTemplateRenderer(origin+"/email/logo.png"),sender,clock,metrics,origin); }
    String state() { return jdbc.queryForObject("SELECT state FROM fintrack.notification_outbox WHERE user_id=? ORDER BY next_attempt_at DESC LIMIT 1",String.class,user); }
    @Test void exactRiskTransitionsCyclesVoidAndArchivedCategoryUseLedgerWithoutTouchingBalance() {
        UUID first=expense(79_999);producer.processEvents(100);assertThat(inboxCount()).isZero();
        expense(1);producer.processEvents(100);assertThat(inboxCount()).isEqualTo(1);
        assertThat(service.list(user,0,20,"ALL").items().getFirst().type()).isEqualTo("BUDGET_WARNING");
        expense(20_000);producer.processEvents(100);assertThat(inboxCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fintrack.notification_outbox WHERE user_id=? AND state='CANCELLED'",Long.class,user)).isEqualTo(1);
        jdbc.update("UPDATE fintrack.categories SET archived_at=now() WHERE id=?",category);
        assertThat(new BudgetRepository(jdbc).detail(user,budget).spentMinor()).isEqualTo(100_000);
        tx.executeWithoutResult(s->transactions.delete(user,first));producer.processEvents(100);
        jdbc.update("UPDATE fintrack.categories SET archived_at=NULL WHERE id=?",category);
        expense(80_000);producer.processEvents(100);assertThat(inboxCount()).isEqualTo(3);
    }
    @Test void jumpToExceededAndThreshold100NeverEmitsWarning() {
        jdbc.update("UPDATE fintrack.notification_settings SET budget_alert_threshold_percent=100 WHERE user_id=?",user);
        expense(100_000);producer.processEvents(100);assertThat(inboxCount()).isEqualTo(1);
        assertThat(service.list(user,0,20,"ALL").items().getFirst().type()).isEqualTo("BUDGET_EXCEEDED");
        producer.evaluate(user,true);assertThat(inboxCount()).isEqualTo(1);
    }
    @Test void togglesDoNotChangeBudgetStatusAndEnableBaselinesInsteadOfReplayingRisk() {
        jdbc.update("UPDATE fintrack.notification_settings SET notifications_enabled=false WHERE user_id=?",user);
        expense(85_000);producer.processEvents(100);assertThat(inboxCount()).isZero();
        assertThat(new BudgetRepository(jdbc).detail(user,budget).status()).isEqualTo("WARNING");
        jdbc.update("UPDATE fintrack.notification_settings SET notifications_enabled=true WHERE user_id=?",user);
        producer.processEvents(100);assertThat(inboxCount()).isZero();
        expense(15_000);producer.processEvents(100);assertThat(inboxCount()).isEqualTo(1);
    }
    @Test void pendingEnableResetCoalescesExpensesIntoBaselineWithoutRetroactiveEmail() {
        jdbc.update("UPDATE fintrack.notification_settings SET notifications_enabled=false WHERE user_id=?",user);
        jdbc.update("UPDATE fintrack.notification_settings SET notifications_enabled=true WHERE user_id=?",user);
        expense(85_000);producer.processEvents(100);assertThat(inboxCount()).isZero();
        expense(15_000);producer.processEvents(100);assertThat(inboxCount()).isEqualTo(1);
    }
    @Test void parentArchiveAndMovedHistoricalContextCancelPendingEmails() {
        expense(85_000);producer.processEvents(100);assertThat(outboxCount()).isEqualTo(1);
        jdbc.update("UPDATE fintrack.monthly_budgets SET archived_at=now() WHERE id=?",parent);
        producer.processEvents(100);assertThat(state()).isEqualTo("CANCELLED");
        assertThat(inboxCount()).isEqualTo(1);
        UUID past=UUID.randomUUID();jdbc.update("INSERT INTO fintrack.monthly_budgets(id,user_id,month) VALUES(?,?,'2026-10-01')",past,user);
        jdbc.update("UPDATE fintrack.budgets SET monthly_budget_id=?,period_start='2026-10-01',period_end='2026-11-01' WHERE id=?",past,budget);
        producer.processEvents(100);assertThat(inboxCount()).isEqualTo(1);
    }
    @Test void financialReplayNoopTransferAndRollbackOnlyAppendActualCommittedExpenseChanges() {
        long before=jdbc.queryForObject("SELECT count(*) FROM fintrack.notification_finance_events",Long.class);
        var r=request(80_000);tx.execute(s->transactions.create(user,r));tx.execute(s->transactions.create(user,r));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fintrack.notification_finance_events",Long.class)).isEqualTo(before+1);
        tx.executeWithoutResult(s->{transactions.create(user,request(1));s.setRollbackOnly();});
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fintrack.notification_finance_events",Long.class)).isEqualTo(before+1);
        UUID target=UUID.randomUUID();jdbc.update("INSERT INTO fintrack.wallets(id,user_id,name,wallet_type) VALUES(?,?,'Target','BANK')",target,user);
        var transfer=request(100);transfer.transactionType="TRANSFER";transfer.categoryId=null;transfer.destinationWalletId=target;
        tx.execute(s->transactions.create(user,transfer));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fintrack.notification_finance_events",Long.class)).isEqualTo(before+1);
        producer.processEvents(100);assertThat(inboxCount()).isEqualTo(1);
    }
    @Test void twoEvaluatorsProduceOneNotificationAndOneOutbox() throws Exception {
        expense(85_000);var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{await(gate);producer.evaluate(user,false);});var b=pool.submit(()->{await(gate);producer.evaluate(user,false);});gate.countDown();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        }
        assertThat(inboxCount()).isEqualTo(1);assertThat(outboxCount()).isEqualTo(1);
    }
    @Test void evaluationUsesOneCommittedSettingsAndBudgetSnapshot() throws Exception {
        expense(85_000);
        var observed=new CountDownLatch(1);var changed=new CountDownLatch(1);
        var consistent=new FinanceNotificationProducer(jdbc,new BudgetRepository(jdbc),repository,manager,clock,origin,metrics) {
            @Override User user(UUID id) {
                User value=super.user(id);
                if(id.equals(user)) { observed.countDown();await(changed); }
                return value;
            }
        };
        try(var pool=Executors.newFixedThreadPool(2)) {
            var read=pool.submit(()->consistent.evaluate(user,false));
            var write=pool.submit(()->{await(observed);try {
                jdbc.update("UPDATE fintrack.notification_settings SET budget_alert_threshold_percent=90 WHERE user_id=?",user);
            } finally { changed.countDown(); }});
            read.get(10,TimeUnit.SECONDS);write.get(10,TimeUnit.SECONDS);
        }
        assertThat(inboxCount()).isEqualTo(1);
        producer.processEvents(100);
        assertThat(state()).isEqualTo("CANCELLED");
    }
    @Test void ownershipPaginationReadDeleteAndTombstoneAreIndependentFromDelivery() {
        UUID id=insert(user,"first");insert(user,"second");insert(other,"other");
        var page=service.list(user,0,1,"UNREAD");assertThat(page.totalPages()).isEqualTo(2);assertThat(page.unreadCount()).isEqualTo(2);
        assertThatThrownBy(()->service.detail(other,id)).isInstanceOf(AppException.class);
        assertThatThrownBy(()->tx.execute(s->service.read(other,id))).isInstanceOf(AppException.class);
        assertThatThrownBy(()->tx.executeWithoutResult(s->service.delete(other,id))).isInstanceOf(AppException.class);
        var read=tx.execute(s->service.read(user,id));assertThat(tx.execute(s->service.read(user,id)).readAt()).isEqualTo(read.readAt());
        tx.executeWithoutResult(s->service.delete(user,id));tx.executeWithoutResult(s->service.delete(user,id));
        assertThatThrownBy(()->service.detail(user,id)).isInstanceOf(AppException.class);
        assertThat(insert(user,"first")).isNull();assertThat(service.count(user).unreadCount()).isEqualTo(1);
        assertThat(service.list(user,99,20,"ALL").items()).isEmpty();
        assertThatThrownBy(()->service.list(user,0,101,"ALL")).isInstanceOf(AppException.class);
        assertThatThrownBy(()->tx.execute(s->service.readAll(user,Instant.now().plusSeconds(3600)))).isInstanceOf(AppException.class);
    }
    @Test void readAllCutoffAndInsertionLockKeepNewItemsUnread() throws Exception {
        insert(user,"old");Instant cutoff=service.count(user).snapshotAt();CountDownLatch lock=new CountDownLatch(1),attempt=new CountDownLatch(1),commit=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var read=pool.submit(()->tx.execute(s->{repository.lockInbox(user);lock.countDown();await(attempt);var result=service.readAll(user,cutoff);commit.countDown();return result;}));
            var write=pool.submit(()->{await(lock);attempt.countDown();return insert(user,"new");});
            assertThat(read.get(10,TimeUnit.SECONDS).affectedCount()).isEqualTo(1);write.get(10,TimeUnit.SECONDS);
        }
        assertThat(service.count(user).unreadCount()).isEqualTo(1);
    }
    @Test void snapshotCutoffPrecedesConcurrentInsertBetweenQueries() throws Exception {
        insert(user,"existing");CountDownLatch snapshot=new CountDownLatch(1),inserted=new CountDownLatch(1);
        var blocked=new NotificationRepository(jdbc) { @Override public Instant now() { Instant result=super.now();snapshot.countDown();await(inserted);return result; } };
        var rr=new TransactionTemplate(manager);rr.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var read=pool.submit(()->rr.execute(s->new NotificationService(blocked).list(user,0,20,"ALL")));
            var write=pool.submit(()->{await(snapshot);UUID id=insert(user,"concurrent");inserted.countDown();return id;});
            var page=read.get(10,TimeUnit.SECONDS);UUID id=write.get(10,TimeUnit.SECONDS);
            assertThat(page.items()).hasSize(1);assertThat(service.detail(user,id).createdAt()).isAfter(page.snapshotAt());
            tx.execute(s->service.readAll(user,page.snapshotAt()));assertThat(service.count(user).unreadCount()).isEqualTo(1);
        }
    }
    @Test void emailConsentDefaultsFalseRejectsUnverifiedAndV1PreservesV2Consent() {
        UUID fresh=UUID.randomUUID();jdbc.update("INSERT INTO fintrack.users(id,username,email,password_hash) VALUES(?,?,?,'hash')",fresh,"n"+fresh,"n"+fresh+"@example.test");
        assertThat(tx.execute(s->settings.get(fresh)).emailFinanceEnabled()).isFalse();
        var enable=new NotificationSettingsV2Service.Patch();enable.emailFinanceEnabled=true;
        assertThatThrownBy(()->tx.execute(s->settings.patch(fresh,enable))).isInstanceOfSatisfying(AppException.class,e->assertThat(e.getStatus().value()).isEqualTo(409));
        tx.execute(s->settings.patch(user,enable));
        var v1=new UserService(new UserRepository(jdbc),new UserPreferencesRepository(jdbc),new NotificationSettingsRepository(jdbc),mock(PasswordEncoder.class),mock(RefreshTokenRotatingService.class));
        tx.execute(s->v1.updateMyNotificationSettings(user,new UpdateNotificationSettingsRequest(null,null,null,null,(short)90,null,null)));
        assertThat(settings.get(user).emailFinanceEnabled()).isTrue();assertThat(settings.get(user).budgetAlertThresholdPercent()).isEqualTo(90);
    }
    @Test void concurrentV1AndV2PartialSettingsKeepConsentAndBothChanges() throws Exception {
        var v1=new UserService(new UserRepository(jdbc),new UserPreferencesRepository(jdbc),new NotificationSettingsRepository(jdbc),mock(PasswordEncoder.class),mock(RefreshTokenRotatingService.class));
        CountDownLatch gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{await(gate);tx.execute(s->v1.updateMyNotificationSettings(user,new UpdateNotificationSettingsRequest(null,null,null,null,(short)90,null,null)));});
            var b=pool.submit(()->{await(gate);var patch=new NotificationSettingsV2Service.Patch();patch.dailyReminderEnabled=false;tx.execute(s->settings.patch(user,patch));});gate.countDown();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        }
        var result=settings.get(user);assertThat(result.emailFinanceEnabled()).isTrue();assertThat(result.budgetAlertThresholdPercent()).isEqualTo(90);assertThat(result.dailyReminderEnabled()).isFalse();
    }
    @Test void twoDailySchedulersDeduplicateRestartAndSettingChangesSameLocalDate() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->producer.daily(100));var b=pool.submit(()->producer.daily(100));a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        }
        assertThat(inboxCount()).isEqualTo(1);producer.daily(100);assertThat(inboxCount()).isEqualTo(1);
        jdbc.update("UPDATE fintrack.notification_settings SET daily_reminder_time='20:30' WHERE user_id=?",user);producer.daily(100);assertThat(inboxCount()).isEqualTo(1);
    }
    @Test void dailyCatchupSkipsOldDaysAndLaterBatchUsersAreNotStarved() {
        clock.value=Instant.parse("2026-11-09T23:01:00Z");producer.daily(1);producer.daily(1);producer.daily(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fintrack.daily_notification_schedule",Long.class)).isEqualTo(2);
        assertThat(inboxCount()).isZero();
        clock.value=Instant.parse("2026-11-10T21:30:00Z");producer.daily(100);assertThat(inboxCount()).isEqualTo(1);
        clock.value=Instant.parse("2026-11-13T22:00:00Z");producer.daily(100);assertThat(inboxCount()).isEqualTo(1);
    }
    @Test void dailyForwardDateGuardDoesNotReplayEarlierDateAfterTimezoneChange() {
        jdbc.update("UPDATE fintrack.user_preferences SET time_zone='Pacific/Kiritimati' WHERE user_id=?",user);
        jdbc.update("UPDATE fintrack.notification_settings SET daily_reminder_time='11:00' WHERE user_id=?",user);producer.daily(100);assertThat(inboxCount()).isEqualTo(1);
        jdbc.update("UPDATE fintrack.user_preferences SET time_zone='UTC' WHERE user_id=?",user);
        jdbc.update("UPDATE fintrack.notification_settings SET daily_reminder_time='21:00' WHERE user_id=?",user);producer.daily(100);assertThat(inboxCount()).isEqualTo(1);
    }
    @Test void transientRetryUsesIdenticalFrozenPayloadAndSentDoesNotReadInbox() {
        expense(85_000);producer.processEvents(100);List<String> html=new ArrayList<>();List<UUID> ids=new ArrayList<>();AtomicInteger attempts=new AtomicInteger();
        var worker=worker((recipient,subject,body,id)->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();html.add(body);ids.add(id);if(attempts.getAndIncrement()==0)throw new ResourceAccessException("private");});
        worker.drain(1);assertThat(state()).isEqualTo("RETRY");
        jdbc.update("UPDATE fintrack.notification_outbox SET next_attempt_at=? WHERE user_id=?",Timestamp.from(clock.instant()),user);
        worker.drain(1);assertThat(state()).isEqualTo("SENT");assertThat(html).hasSize(2);assertThat(html.get(0)).isEqualTo(html.get(1));assertThat(ids.get(0)).isEqualTo(ids.get(1));
        assertThat(service.count(user).unreadCount()).isEqualTo(1);
    }
    @Test void leaseRecoveryFencesOldWorkerAndOnlyCurrentOwnerCanFinalize() {
        expense(85_000);producer.processEvents(100);var worker=worker((a,b,c,d)->{});var old=worker.claim();
        assertThat(old).isNotNull();jdbc.update("UPDATE fintrack.notification_outbox SET lease_until=? WHERE delivery_id=?",Timestamp.from(clock.instant().minusSeconds(1)),old.id);
        var replacement=worker.claim();assertThat(replacement.id).isEqualTo(old.id);assertThat(replacement.token).isNotEqualTo(old.token);
        worker.finish(old,"SENT","ACCEPTED",null);assertThat(state()).isEqualTo("IN_FLIGHT");
        worker.finish(replacement,"SENT","ACCEPTED",null);assertThat(state()).isEqualTo("SENT");
    }
    @Test void pendingEmailIsCancelledForRecipientDisableVoidDeletedAndExpiredChanges() {
        for(String change:List.of("recipient","disable","void","deleted","expiry")) {
            jdbc.update("UPDATE fintrack.notification_settings SET email_finance_enabled=true WHERE user_id=?",user);producer.processEvents(100);
            UUID entry=expense(85_000);producer.processEvents(100);
            switch(change) {
                case "recipient"->jdbc.update("UPDATE fintrack.users SET email=? WHERE id=?","changed"+UUID.randomUUID()+"@example.test",user);
                case "disable"->jdbc.update("UPDATE fintrack.notification_settings SET email_finance_enabled=false WHERE user_id=?",user);
                case "void"->tx.executeWithoutResult(s->transactions.delete(user,entry));
                case "deleted"->tx.executeWithoutResult(s->service.delete(user,service.list(user,0,100,"ALL").items().getFirst().notificationId()));
                case "expiry"->jdbc.update("UPDATE fintrack.notification_outbox SET expires_at=? WHERE user_id=? AND state='PENDING'",Timestamp.from(clock.instant().minusSeconds(1)),user);
            }
            var calls=new AtomicInteger();worker((a,b,c,d)->calls.incrementAndGet()).drain(100);assertThat(calls.get()).as(change).isZero();
            if(!change.equals("void"))tx.executeWithoutResult(s->transactions.delete(user,entry));producer.processEvents(100);
        }
    }
    @Test void malformedUserCannotBlockOtherUsersInSameEventBatch() {
        expense(85_000);jdbc.update("UPDATE fintrack.user_preferences SET time_zone='Not/AZone' WHERE user_id=?",other);
        producer.processEvents(100);assertThat(inboxCount()).isEqualTo(1);
        assertThat(metrics.counter("fintrack.notification.user.failure","operation","events").count()).isGreaterThan(0);
    }
    static void await(CountDownLatch latch) { try { if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("barrier timeout"); }catch(InterruptedException error){throw new RuntimeException(error);} }
    static class MutableClock extends Clock {
        Instant value;MutableClock(Instant value){this.value=value;}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return value;}
    }
}
