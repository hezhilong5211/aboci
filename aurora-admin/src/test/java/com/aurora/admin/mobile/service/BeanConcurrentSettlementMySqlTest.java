package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.mapper.BeanAccountMapper;
import com.aurora.admin.mobile.service.impl.BeanAccountServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real REPEATABLE READ transactions: the waiter establishes an old snapshot before its account lock. */
@SpringBootTest(classes = BeanConcurrentSettlementMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.flyway.enabled=false", "spring.task.scheduling.enabled=false",
                "spring.datasource.hikari.maximum-pool-size=4"})
@EnabledIf("databaseAvailable")
class BeanConcurrentSettlementMySqlTest {
    private static final long USER_ID = 901001L;
    private static MySQLContainer<?> container;

    @Autowired
    private IBeanAccountService beans;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    static boolean databaseAvailable() {
        return externalUrl() != null || DockerClientFactory.instance().isDockerAvailable();
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        if (externalUrl() != null) {
            if (!externalUrl().matches("jdbc:mysql://[^/]+/[a-zA-Z0-9_]+_test(?:\\?.*)?")) {
                throw new IllegalArgumentException("External database must be a dedicated *_test schema");
            }
            registry.add("spring.datasource.url", BeanConcurrentSettlementMySqlTest::externalUrl);
            registry.add("spring.datasource.username", () -> System.getenv("AURORA_TEST_MYSQL_USERNAME"));
            registry.add("spring.datasource.password", () -> System.getenv("AURORA_TEST_MYSQL_PASSWORD"));
        } else {
            container = new MySQLContainer<>("mysql:8.0.36")
                    .withDatabaseName("aurora_bean_concurrency_test").withUsername("root").withPassword("aurora-test");
            container.start();
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
        }
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @AfterAll
    static void stopOwnedDatabase() {
        if (container != null) container.stop();
    }

    @BeforeEach
    void resetDedicatedSchema() {
        jdbc.execute("DROP TABLE IF EXISTS bean_transaction");
        jdbc.execute("DROP TABLE IF EXISTS bean_account");
        jdbc.execute("""
                CREATE TABLE bean_account (
                    account_id BIGINT NOT NULL AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    balance BIGINT NOT NULL DEFAULT 0,
                    total_earned BIGINT NOT NULL DEFAULT 0,
                    total_consumed BIGINT NOT NULL DEFAULT 0,
                    total_expired BIGINT NOT NULL DEFAULT 0,
                    total_revoked BIGINT NOT NULL DEFAULT 0,
                    create_time DATETIME NULL,
                    update_time DATETIME NULL,
                    PRIMARY KEY (account_id), UNIQUE KEY uk_user_id (user_id)
                ) ENGINE=InnoDB
                """);
        jdbc.execute("""
                CREATE TABLE bean_transaction (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    type VARCHAR(16) NOT NULL,
                    change_value BIGINT NOT NULL,
                    balance_after BIGINT NOT NULL,
                    biz_no VARCHAR(64) NULL,
                    source_biz_no VARCHAR(96) NULL,
                    remark VARCHAR(255) NULL,
                    create_time DATETIME NULL,
                    PRIMARY KEY (id), KEY idx_user_type_biz (user_id, type, biz_no)
                ) ENGINE=InnoDB
                """);
    }

    @Test
    void sameDebitBizNoIsChargedOnceEvenWhenWaiterHasOlderSnapshot() throws Exception {
        account(100, 0);
        List<Long> debits = raceWithOldSnapshot(
                () -> beans.deductUpToBalance(USER_ID, 80, "same-text", "first request"),
                () -> beans.deductUpToBalance(USER_ID, 80, "same-text", "same request retried"));
        assertEquals(List.of(80L, 80L), debits);
        assertEquals(20, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(80, number("SELECT total_consumed FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(1, number("SELECT COUNT(*) FROM bean_transaction WHERE type='DEDUCT' AND biz_no='same-text'"));
        assertEquals(-80, number("SELECT SUM(change_value) FROM bean_transaction WHERE type='DEDUCT'"));
    }

    @Test
    void sameRefundBizNoIsReturnedOnceEvenWhenWaiterHasOlderSnapshot() throws Exception {
        account(20, 80);
        raceWithOldSnapshot(() -> {
            beans.refund(USER_ID, 80, "same-image-refund", "first refund");
            return true;
        }, () -> {
            beans.refund(USER_ID, 80, "same-image-refund", "duplicate refund");
            return true;
        });
        assertEquals(100, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(0, number("SELECT total_consumed FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(1, number("SELECT COUNT(*) FROM bean_transaction WHERE type='REFUND' AND biz_no='same-image-refund'"));
        assertEquals(80, number("SELECT SUM(change_value) FROM bean_transaction WHERE type='REFUND'"));
    }

    @Test
    void separateConversationsSettleEightyAndTwentyAgainstOneHundredWithoutLostOrExcessDebit() throws Exception {
        account(100, 0);
        List<Long> debits = raceWithOldSnapshot(
                () -> beans.deductUpToBalance(USER_ID, 80, "conversation-a", "A text"),
                () -> beans.deductUpToBalance(USER_ID, 80, "conversation-b", "B text"));
        assertEquals(List.of(80L, 20L), debits);
        assertEquals(0, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(100, number("SELECT total_consumed FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(2, number("SELECT COUNT(*) FROM bean_transaction WHERE type='DEDUCT'"));
        assertEquals(-100, number("SELECT SUM(change_value) FROM bean_transaction WHERE type='DEDUCT'"));
        assertEquals(0, number("SELECT balance_after FROM bean_transaction WHERE biz_no='conversation-b'"));
    }

    @Test
    void zeroDebitRemainsClosedWhenBalanceArrivesLater() {
        account(0, 0);
        assertEquals(0, beans.deductUpToBalance(USER_ID, 80, "empty-text", "settled with empty balance"));
        beans.recharge(USER_ID, 100, "later-recharge", "new balance");
        assertEquals(0, beans.deductUpToBalance(USER_ID, 80, "empty-text", "retry after recharge"));
        assertEquals(100, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(1, number("SELECT COUNT(*) FROM bean_transaction WHERE type='DEDUCT' AND biz_no='empty-text'"));
    }

    private <T> List<T> raceWithOldSnapshot(Supplier<T> first, Supplier<T> second) throws Exception {
        CountDownLatch accountLocked = new CountDownLatch(1);
        CountDownLatch snapshotEstablished = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var firstResult = workers.submit(() -> repeatableRead().execute(status -> {
                assertEquals("REPEATABLE-READ", jdbc.queryForObject("SELECT @@transaction_isolation", String.class));
                beans.lockAccount(USER_ID);
                accountLocked.countDown();
                await(snapshotEstablished);
                assertTrue(waitForAccountLock(), "the second transaction must be waiting for the first account lock");
                return first.get();
            }));
            var secondResult = workers.submit(() -> {
                await(accountLocked);
                return repeatableRead().execute(status -> {
                    assertEquals("REPEATABLE-READ", jdbc.queryForObject("SELECT @@transaction_isolation", String.class));
                    // These plain reads create the stale RR snapshot that previously hid the just-committed ledger row.
                    assertEquals(0, number("SELECT COUNT(*) FROM bean_transaction WHERE user_id = ?", USER_ID));
                    number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID);
                    snapshotEstablished.countDown();
                    return second.get();
                });
            });
            T left = firstResult.get(15, TimeUnit.SECONDS);
            T right = secondResult.get(15, TimeUnit.SECONDS);
            assertFalse(firstResult.isCancelled());
            assertFalse(secondResult.isCancelled());
            return List.of(left, right);
        }
    }

    private boolean waitForAccountLock() {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            long waiting = number("""
                    SELECT COUNT(*) FROM performance_schema.data_lock_waits waits
                    JOIN performance_schema.data_locks locks
                      ON waits.REQUESTING_ENGINE_LOCK_ID = locks.ENGINE_LOCK_ID
                    WHERE locks.OBJECT_SCHEMA = DATABASE() AND locks.OBJECT_NAME = 'bean_account'
                    """);
            if (waiting > 0) return true;
            try { Thread.sleep(20); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        }
        return false;
    }

    private TransactionTemplate repeatableRead() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.setTimeout(12);
        return tx;
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS), "concurrent transaction rendezvous timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    private void account(long balance, long consumed) {
        jdbc.update("INSERT INTO bean_account(user_id,balance,total_earned,total_consumed,create_time,update_time) "
                + "VALUES(?,?,?,?,NOW(),NOW())", USER_ID, balance, balance + consumed, consumed);
    }

    private long number(String sql, Object... args) {
        Number value = jdbc.queryForObject(sql, Number.class, args);
        return value == null ? 0 : value.longValue();
    }

    private static String externalUrl() {
        String url = System.getenv("AURORA_TEST_MYSQL_URL");
        return url == null || url.isBlank() ? null : url;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = BeanAccountMapper.class)
    @Import(BeanAccountServiceImpl.class)
    static class TestApplication {}
}
