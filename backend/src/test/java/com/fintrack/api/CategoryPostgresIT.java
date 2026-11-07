package com.fintrack.api;

import static org.assertj.core.api.Assertions.*;
import com.fintrack.api.dto.request.category.CategoryRequest;
import com.fintrack.api.dto.request.category.CategoryStatusRequest;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.repository.CategoryRepository;
import com.fintrack.api.service.category.CategorySeedService;
import com.fintrack.api.service.category.CategoryService;
import com.fintrack.api.service.finance.FinanceSupport;
import java.util.UUID;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

class CategoryPostgresIT {
    private static PostgreSQLContainer<?> container;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static CategoryService service;
    private static CategorySeedService seeds;
    private static FinanceSupport support;
    private UUID owner;

    @BeforeAll static void database() {
        String url=System.getenv("FINANCE_TEST_JDBC_URL");
        String user=System.getenv().getOrDefault("FINANCE_TEST_DB_USER","postgres");
        String password=System.getenv().getOrDefault("FINANCE_TEST_DB_PASSWORD","");
        if(url==null || url.isBlank()) {
            Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),"PostgreSQL requires FINANCE_TEST_JDBC_URL or Docker");
            container=new PostgreSQLContainer<>("postgres:17.7"); container.start();
            url=container.getJdbcUrl(); user=container.getUsername(); password=container.getPassword();
        }
        Flyway.configure().dataSource(url,user,password).defaultSchema("fintrack").load().migrate();
        var dataSource=new DriverManagerDataSource(url,user,password);
        jdbc=new JdbcTemplate(dataSource); tx=new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        seeds=new CategorySeedService(jdbc); service=new CategoryService(new CategoryRepository(jdbc),seeds);
        support=new FinanceSupport(jdbc);
    }
    @AfterAll static void shutdown() { if(container!=null) container.stop(); }
    @BeforeEach void user() {
        owner=UUID.randomUUID();
        jdbc.update("INSERT INTO fintrack.users(id,username,email,password_hash) VALUES (?,?,?,?)",
                owner,"category_"+owner,"category_"+owner+"@example.test","test-only");
    }
    @AfterEach void cleanup() {
        // Foreign keys preserve history; remove test fixtures in dependency order.
        jdbc.update("DELETE FROM fintrack.budgets WHERE user_id=?",owner);
        jdbc.update("DELETE FROM fintrack.transactions WHERE user_id=?",owner);
        jdbc.update("DELETE FROM fintrack.categories WHERE user_id=?",owner);
        jdbc.update("DELETE FROM fintrack.users WHERE id=?",owner);
    }
    private CategoryRequest request(String name,String type) {
        CategoryRequest r=new CategoryRequest(); r.name=name; r.categoryType=type; return r;
    }
    private UUID create(String name) { return tx.execute(s->service.create(owner,request(name,"EXPENSE"))).categoryId(); }
    private void assertError(Runnable action,ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AppException.class,e->assertThat(e.getErrorCode()).isEqualTo(code));
    }
    @Test void seedOnceAndPreserveArchivedOrRenamedHistory() {
        tx.executeWithoutResult(s->seeds.initialize(owner));
        assertThat(service.list(owner,null,null)).hasSize(8);
        UUID id=service.list(owner,null,"INCOME").getFirst().categoryId();
        tx.executeWithoutResult(s->service.update(owner,id,request("Renamed salary","INCOME")));
        for(var c:service.list(owner,null,null)) tx.executeWithoutResult(s->service.archive(owner,c.categoryId()));
        tx.executeWithoutResult(s->seeds.initialize(owner));
        assertThat(service.list(owner,"ACTIVE",null)).isEmpty();
        assertThat(service.list(owner,"ALL",null)).hasSize(8).anyMatch(c->c.name().equals("Renamed salary"));
    }
    @Test void existingArchivedCustomCategoryMeansNoSeeds() {
        jdbc.update("INSERT INTO fintrack.categories(user_id,name,category_type,archived_at) VALUES (?,'Original','EXPENSE',now())",owner);
        tx.executeWithoutResult(s->seeds.initialize(owner));
        assertThat(service.list(owner,null,null)).hasSize(1);
    }
    @Test void ownershipUniquenessRestoreAndHistoricalReferenceRules() {
        UUID id=create("Custom");
        assertError(()->service.detail(UUID.randomUUID(),id),ErrorCode.NOT_FOUND);
        assertError(()->tx.executeWithoutResult(s->service.create(owner,request("custom","EXPENSE"))),ErrorCode.CATEGORY_NAME_CONFLICT);
        tx.executeWithoutResult(s->service.create(owner,request("Custom","INCOME")));
        tx.executeWithoutResult(s->service.archive(owner,id));
        var archivedAt=service.detail(owner,id).archivedAt();
        tx.executeWithoutResult(s->service.archive(owner,id));
        assertThat(service.detail(owner,id).archivedAt()).isEqualTo(archivedAt);
        UUID replacement=create("Custom");
        CategoryStatusRequest restore=new CategoryStatusRequest(); restore.active=true;
        assertError(()->tx.executeWithoutResult(s->service.status(owner,id,restore)),ErrorCode.CATEGORY_NAME_CONFLICT);
        jdbc.update("INSERT INTO fintrack.budgets(user_id,category_id,period_start,period_end,limit_minor,archived_at) VALUES (?,?,'2026-11-01','2026-12-01',100,now())",owner,replacement);
        assertError(()->tx.executeWithoutResult(s->service.update(owner,replacement,request("Custom","INCOME"))),ErrorCode.CATEGORY_TYPE_IN_USE);
        UUID txCategory=create("Voided reference");
        jdbc.update("INSERT INTO fintrack.transactions(user_id,transaction_type,amount_minor,category_id,occurred_at,status,voided_at) VALUES (?,'EXPENSE',100,?,now(),'VOIDED',now())",owner,txCategory);
        assertError(()->tx.executeWithoutResult(s->service.update(owner,txCategory,request("Was expense","INCOME"))),ErrorCode.CATEGORY_TYPE_IN_USE);
    }
    @Test void concurrentReferenceWriterSerializesWithTypeChange() throws Exception {
        UUID id=create("Concurrent");
        CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1);
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            Future<?> reference=executor.submit(()->tx.executeWithoutResult(s->{
                assertThat(support.category(owner,id,true).categoryType()).isEqualTo("EXPENSE");
                locked.countDown();
                try { if(!release.await(10,TimeUnit.SECONDS)) throw new AssertionError("Timed out"); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                jdbc.update("INSERT INTO fintrack.budgets(user_id,category_id,period_start,period_end,limit_minor) VALUES (?,?,'2026-11-01','2026-12-01',100)",owner,id);
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            Future<?> change=executor.submit(()->assertError(()->tx.executeWithoutResult(s->
                    service.update(owner,id,request("Concurrent","INCOME"))),ErrorCode.CATEGORY_TYPE_IN_USE));
            release.countDown(); reference.get(10,TimeUnit.SECONDS); change.get(10,TimeUnit.SECONDS);
            assertThat(service.detail(owner,id).categoryType()).isEqualTo("EXPENSE");
        } finally { release.countDown(); executor.shutdownNow(); }
    }
    @Test void concurrentCreatesAllowExactlyOneActiveName() throws Exception {
        tx.executeWithoutResult(s->seeds.initialize(owner));
        ExecutorService executor=Executors.newFixedThreadPool(2);
        CountDownLatch start=new CountDownLatch(1);
        Callable<Boolean> create=()->{ start.await(); try { create("Race"); return true; }
            catch(AppException e) { assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CATEGORY_NAME_CONFLICT); return false; } };
        try {
            Future<Boolean> first=executor.submit(create), second=executor.submit(create); start.countDown();
            assertThat(first.get(10,TimeUnit.SECONDS)^second.get(10,TimeUnit.SECONDS)).isTrue();
        } finally { executor.shutdownNow(); }
    }
}
