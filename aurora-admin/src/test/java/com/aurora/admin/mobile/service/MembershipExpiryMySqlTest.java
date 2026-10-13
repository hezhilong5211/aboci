package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.controller.MobileBeanController;
import com.aurora.admin.mobile.domain.MobileUser;
import com.aurora.admin.mobile.mapper.BeanAccountMapper;
import com.aurora.admin.mobile.service.impl.BeanAccountServiceImpl;
import com.aurora.admin.mobile.service.impl.BeanTransactionServiceImpl;
import com.aurora.admin.mobile.service.impl.MembershipExpiryServiceImpl;
import com.aurora.admin.mobile.service.impl.UserMembershipServiceImpl;
import com.aurora.framework.mobile.MobileAuthEntryPoint;
import com.aurora.framework.mobile.MobileJwtFilter;
import com.aurora.framework.mobile.MobileTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.sql.Connection;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 使用真实 MySQL 验证 SELECT FOR UPDATE、原子清零 SQL、EXPIRE 流水与事务幂等。
 */
@SpringBootTest(
        classes = MembershipExpiryMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.flyway.enabled=false",
                "spring.task.scheduling.enabled=false"
        })
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
class MembershipExpiryMySqlTest {

    private static final long USER_ID = 1001L;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("aurora_expiry_test")
            .withUsername("aurora")
            .withPassword("aurora");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private IMembershipExpiryService membershipExpiryService;

    @Autowired
    private IBeanAccountService beanAccountService;

    @Autowired
    private IUserMembershipService userMembershipService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS pay_order");
        jdbcTemplate.execute("DROP TABLE IF EXISTS membership_level");
        jdbcTemplate.execute("DROP TABLE IF EXISTS bean_transaction");
        jdbcTemplate.execute("DROP TABLE IF EXISTS user_membership");
        jdbcTemplate.execute("DROP TABLE IF EXISTS bean_account");

        jdbcTemplate.execute("""
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
                    PRIMARY KEY (account_id),
                    UNIQUE KEY uk_user_id (user_id)
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.execute("""
                CREATE TABLE bean_transaction (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    type VARCHAR(16) NOT NULL,
                    change_value BIGINT NOT NULL,
                    balance_after BIGINT NOT NULL,
                    biz_no VARCHAR(64) NULL,
                    source_biz_no VARCHAR(128) NULL,
                    remark VARCHAR(255) NULL,
                    create_time DATETIME NULL,
                    PRIMARY KEY (id),
                    KEY idx_user_create (user_id, create_time),
                    KEY idx_user_type_biz (user_id, type, biz_no),
                    UNIQUE KEY uk_type_source_biz (type, source_biz_no)
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.execute("""
                CREATE TABLE user_membership (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    level_id BIGINT NULL,
                    level_sort INT NOT NULL DEFAULT 0,
                    level_name VARCHAR(64) NOT NULL,
                    snapshot_grant_beans BIGINT NOT NULL DEFAULT 0,
                    snapshot_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                    source VARCHAR(16) NOT NULL,
                    source_order_no VARCHAR(64) NULL,
                    paid_time DATETIME NULL,
                    start_time DATETIME NOT NULL,
                    expire_time DATETIME NOT NULL,
                    expiry_settled_time DATETIME NULL,
                    status CHAR(1) NOT NULL DEFAULT '0',
                    create_by VARCHAR(64) NULL,
                    create_time DATETIME NULL,
                    update_by VARCHAR(64) NULL,
                    update_time DATETIME NULL,
                    remark VARCHAR(255) NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_user_membership_source_order (source_order_no),
                    KEY idx_user_status (user_id, status),
                    KEY idx_status_expire_user (status, expire_time, user_id),
                    KEY idx_status_settled_expire_user (status, expiry_settled_time, expire_time, user_id)
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.execute("""
                CREATE TABLE pay_order (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    order_no VARCHAR(64) NULL,
                    pay_time DATETIME NULL,
                    status TINYINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_order_no (order_no)
                ) ENGINE=InnoDB
                """);
    }

    @Test
    void 会员赠豆到期失效_累计过期不计入真实消费_重复执行幂等() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 8, 0);
        LocalDateTime expireTime = start.plusDays(MembershipPolicy.DURATION_DAYS);
        insertAccount(USER_ID, 3450L, 4050L, 600L);
        insertMembership(1L, USER_ID, "0", start, expireTime);

        long first = membershipExpiryService.settleUserIfExpired(USER_ID, expireTime);

        assertEquals(3450L, first);
        assertEquals(0L, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(4050L, number("SELECT total_earned FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(600L, number("SELECT total_consumed FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(3450L, number("SELECT total_expired FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(1L, number("SELECT COUNT(*) FROM bean_transaction WHERE user_id = ? AND type = 'EXPIRE'", USER_ID));
        assertEquals(-3450L, number("SELECT change_value FROM bean_transaction WHERE user_id = ? AND type = 'EXPIRE'", USER_ID));
        assertEquals(0L, number("SELECT balance_after FROM bean_transaction WHERE user_id = ? AND type = 'EXPIRE'", USER_ID));
        assertEquals(1L, number("SELECT COUNT(*) FROM user_membership WHERE user_id = ? AND status = '1'", USER_ID));
        assertEquals(1L, number("SELECT COUNT(*) FROM user_membership WHERE user_id = ? AND expiry_settled_time IS NOT NULL", USER_ID));

        // 旧会员段已做一次性结算后，即使账户又有新到账，重复任务也不得误清。
        jdbcTemplate.update("UPDATE bean_account SET balance = 777, total_earned = total_earned + 777 WHERE user_id = ?",
                USER_ID);
        long second = membershipExpiryService.settleUserIfExpired(USER_ID, expireTime.plusMinutes(1));

        assertEquals(0L, second);
        assertEquals(777L, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(3450L, number("SELECT total_expired FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(1L, number("SELECT COUNT(*) FROM bean_transaction WHERE user_id = ? AND type = 'EXPIRE'", USER_ID));
    }

    @Test
    void 另365天未到不清_精确到期时才清() {
        LocalDateTime start = LocalDateTime.of(2026, 4, 1, 9, 30);
        LocalDateTime expireTime = start.plusDays(MembershipPolicy.DURATION_DAYS);
        insertAccount(USER_ID, 2000L, 2000L, 0L);
        insertMembership(1L, USER_ID, "0", start, expireTime);

        assertEquals(0L, membershipExpiryService.settleUserIfExpired(USER_ID, expireTime.minusNanos(1)));
        assertEquals(2000L, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals("0", jdbcTemplate.queryForObject(
                "SELECT status FROM user_membership WHERE id = 1", String.class));

        assertEquals(2000L, membershipExpiryService.settleUserIfExpired(USER_ID, expireTime));
        assertEquals(0L, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals("1", jdbcTemplate.queryForObject(
                "SELECT status FROM user_membership WHERE id = 1", String.class));
    }

    @Test
    void 会员到期仅失效会员赠豆_加油包豆永久保留() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 1, 0, 0);
        insertAccount(USER_ID, 0L, 0L, 0L);
        beanAccountService.issue(USER_ID, 2000L, "member-issue", "会员赠豆");
        beanAccountService.recharge(USER_ID, 1000L, "permanent-addon", "永久加油包");
        beanAccountService.deduct(USER_ID, 500L, "generation-before-expiry", "生成扣豆");
        insertMembership(1L, USER_ID, "0",
                cutoff.minusDays(MembershipPolicy.DURATION_DAYS), cutoff);

        long expired = membershipExpiryService.settleUserIfExpired(USER_ID, cutoff);

        assertEquals(1500L, expired);
        assertEquals(1000L, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(1500L, number("SELECT total_expired FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(-1500L, number("SELECT change_value FROM bean_transaction "
                + "WHERE user_id = ? AND type = 'EXPIRE'", USER_ID));
    }

    @Test
    void 存在另一有效会员段时不清余额() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 6, 1, 0, 0);
        insertAccount(USER_ID, 4000L, 4000L, 0L);
        insertMembership(1L, USER_ID, "0", cutoff.minusDays(30), cutoff);
        insertMembership(2L, USER_ID, "0", cutoff.minusDays(2), cutoff.plusDays(28));

        assertEquals(0L, membershipExpiryService.settleUserIfExpired(USER_ID, cutoff));

        assertEquals(4000L, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(0L, number("SELECT total_expired FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(0L, number("SELECT COUNT(*) FROM bean_transaction WHERE user_id = ? AND type = 'EXPIRE'", USER_ID));
        assertEquals("1", jdbcTemplate.queryForObject(
                "SELECT status FROM user_membership WHERE id = 1", String.class));
        assertEquals("0", jdbcTemplate.queryForObject(
                "SELECT status FROM user_membership WHERE id = 2", String.class));
    }

    @Test
    void 双线程同时结算只产生一条EXPIRE流水() throws Exception {
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 1, 0, 0);
        insertAccount(USER_ID, 3450L, 4050L, 600L);
        insertMembership(1L, USER_ID, "0", cutoff.minusDays(30), cutoff);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Long> action = () -> {
                ready.countDown();
                start.await();
                return membershipExpiryService.settleUserIfExpired(USER_ID, cutoff);
            };
            Future<Long> first = executor.submit(action);
            Future<Long> second = executor.submit(action);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            assertEquals(3450L, first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertEquals(0L, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(3450L, number("SELECT total_expired FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(1L, number("SELECT COUNT(*) FROM bean_transaction WHERE user_id = ? AND type = 'EXPIRE'", USER_ID));
        assertEquals(1L, number("SELECT COUNT(*) FROM user_membership WHERE id = 1 AND expiry_settled_time IS NOT NULL"));
    }

    @Test
    void 到期结算与支付开通并发_仅旧100豆过期_新会员2000豆完整保留() throws Exception {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(1);
        insertAccount(USER_ID, 100L, 100L, 0L);
        insertMembership(1L, USER_ID, "0", cutoff.minusDays(30), cutoff);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Long> expiry = executor.submit(() -> {
                ready.countDown();
                start.await();
                return membershipExpiryService.settleUserIfExpired(USER_ID, LocalDateTime.now());
            });
            Future<Void> grant = executor.submit(() -> {
                ready.countDown();
                start.await();
                userMembershipService.grantFromPayment(
                        USER_ID, 1L, 1, "青铜会员", 2000L,
                        new BigDecimal("1.99"), MembershipPolicy.DURATION_DAYS,
                        "membership-pay-concurrent-1");
                return null;
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            long clearedByExternalExpiry = expiry.get(20, TimeUnit.SECONDS);
            grant.get(20, TimeUnit.SECONDS);
            assertTrue(clearedByExternalExpiry == 0L || clearedByExternalExpiry == 100L,
                    "旧豆可能由外部结算线程或开通事务内的惰性结算线程清理");
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertEquals(2000L, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(2100L, number("SELECT total_earned FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(100L, number("SELECT total_expired FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(1L, number("SELECT COUNT(*) FROM bean_transaction WHERE user_id = ? AND type = 'EXPIRE'", USER_ID));
        assertEquals(1L, number("SELECT COUNT(*) FROM bean_transaction WHERE user_id = ? AND type = 'ISSUE' "
                + "AND change_value = 2000 AND balance_after = 2000", USER_ID));
        assertEquals(1L, number("SELECT COUNT(*) FROM user_membership WHERE id = 1 "
                + "AND status = '1' AND expiry_settled_time IS NOT NULL"));
        assertEquals(1L, number("SELECT COUNT(*) FROM user_membership "
                + "WHERE source_order_no = 'membership-pay-concurrent-1' AND status = '0' "
                + "AND paid_time IS NOT NULL "
                + "AND TIMESTAMPDIFF(DAY, start_time, expire_time) = 365"));
    }

    @Test
    void B在T先提交_A在T前一秒支付后处理_仅凭会员段支付时间保持正确顺序() {
        LocalDateTime boundary = LocalDateTime.of(2026, 7, 10, 12, 0);
        LocalDateTime paidAtA = boundary.minusSeconds(1);
        LocalDateTime paidAtB = boundary;
        LocalDateTime processedAtA = boundary.plusSeconds(2);
        String orderA = "mysql-paid-a-before-expiry";
        String orderB = "mysql-paid-b-at-expiry";

        insertAccount(USER_ID, 0L, 0L, 0L);
        insertMembership(1L, USER_ID, "0", boundary.minusDays(30), boundary);

        // 模拟回调乱序：后付款的 B 先在独立事务完成并提交。
        userMembershipService.grantFromPayment(
                USER_ID, 1L, 1, "青铜会员", 0L, new BigDecimal("1.99"),
                MembershipPolicy.DURATION_DAYS, orderB, paidAtB, paidAtB);
        // A 后处理时已能在 FOR UPDATE 结果中直接看到 B 的 paid_time，完全无需再查 pay_order。
        userMembershipService.grantFromPayment(
                USER_ID, 1L, 1, "青铜会员", 0L, new BigDecimal("1.99"),
                MembershipPolicy.DURATION_DAYS, orderA, paidAtA, processedAtA);

        assertEquals(paidAtA, dateTime(
                "SELECT paid_time FROM user_membership WHERE source_order_no = ?", orderA));
        assertEquals(paidAtB, dateTime(
                "SELECT paid_time FROM user_membership WHERE source_order_no = ?", orderB));
        assertEquals(boundary, dateTime(
                "SELECT start_time FROM user_membership WHERE source_order_no = ?", orderA));
        assertEquals(boundary.plusDays(MembershipPolicy.DURATION_DAYS), dateTime(
                "SELECT expire_time FROM user_membership WHERE source_order_no = ?", orderA));
        assertEquals(boundary.plusDays(MembershipPolicy.DURATION_DAYS), dateTime(
                "SELECT start_time FROM user_membership WHERE source_order_no = ?", orderB));
        assertEquals(boundary.plusDays(MembershipPolicy.DURATION_DAYS * 2L), dateTime(
                "SELECT expire_time FROM user_membership WHERE source_order_no = ?", orderB));
    }

    @Test
    void 批量扫描只选中到期未结算用户_第二轮无重复候选() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 8, 1, 0, 0);
        long justExpiredUser = 1001L;
        long activeUser = 1002L;
        long historicalUnsettledUser = 1003L;
        long alreadySettledUser = 1004L;

        insertAccount(justExpiredUser, 100L, 100L, 0L);
        insertMembership(1L, justExpiredUser, "0", cutoff.minusDays(30), cutoff);

        insertAccount(activeUser, 200L, 200L, 0L);
        insertMembership(2L, activeUser, "0", cutoff.minusDays(1), cutoff.plusDays(29));

        insertAccount(historicalUnsettledUser, 300L, 300L, 0L);
        insertMembership(3L, historicalUnsettledUser, "1", cutoff.minusDays(40), cutoff.minusDays(10));

        insertAccount(alreadySettledUser, 400L, 400L, 0L);
        insertMembership(4L, alreadySettledUser, "1", cutoff.minusDays(40), cutoff.minusDays(10));
        jdbcTemplate.update("UPDATE user_membership SET expiry_settled_time = ? WHERE id = 4",
                cutoff.minusDays(10));

        int firstBatch = membershipExpiryService.settleExpiredBatch(cutoff, 10);
        int secondBatch = membershipExpiryService.settleExpiredBatch(cutoff.plusMinutes(1), 10);

        assertEquals(2, firstBatch, "刚到期与历史未结算用户应被候选SQL选中");
        assertEquals(0, secondBatch, "一次性标记写入后不得再成为候选");
        assertEquals(0L, number("SELECT balance FROM bean_account WHERE user_id = ?", justExpiredUser));
        assertEquals(200L, number("SELECT balance FROM bean_account WHERE user_id = ?", activeUser));
        assertEquals(0L, number("SELECT balance FROM bean_account WHERE user_id = ?", historicalUnsettledUser));
        assertEquals(400L, number("SELECT balance FROM bean_account WHERE user_id = ?", alreadySettledUser));
        assertEquals(2L, number("SELECT COUNT(*) FROM bean_transaction WHERE type = 'EXPIRE'"));
        assertEquals(2L, number("SELECT COUNT(*) FROM user_membership "
                + "WHERE id IN (1, 3) AND expiry_settled_time IS NOT NULL"));
    }

    @Test
    void 已结算会员的晚到退款只过期退款数量_保留其他合法新余额() {
        LocalDateTime expiredAt = LocalDateTime.of(2026, 1, 31, 0, 0);
        insertAccount(USER_ID, 777L, 4277L, 50L);
        jdbcTemplate.update("UPDATE bean_transaction SET biz_no = 'task-refund-1' "
                + "WHERE user_id = ? AND type = 'DEDUCT' AND biz_no = 'seed-consume-1001'", USER_ID);
        insertMembership(1L, USER_ID, "1", expiredAt.minusDays(30), expiredAt);
        jdbcTemplate.update("UPDATE bean_account SET total_expired = 3450 WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("UPDATE user_membership SET expiry_settled_time = ? WHERE id = 1", expiredAt);

        beanAccountService.refund(USER_ID, 50L, "task-refund-1", "生成失败退豆");
        long expired = membershipExpiryService.expirePostExpiryRefund(
                USER_ID, 50L, "post-expiry-refund:task-refund-1", "会员已到期，晚到退款豆同步清零");

        assertEquals(50L, expired);
        assertEquals(777L, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(0L, number("SELECT total_consumed FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(3500L, number("SELECT total_expired FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(1L, number("SELECT COUNT(*) FROM bean_transaction WHERE type = 'REFUND'"));
        assertEquals(1L, number("SELECT COUNT(*) FROM bean_transaction WHERE type = 'EXPIRE' "
                + "AND change_value = -50 AND balance_after = 777"));
    }

    @Test
    void 旧任务退豆不混入新会员_后证实连续续费可恢复原批次并精确退单() {
        LocalDateTime now = MembershipPolicy.now();
        LocalDateTime oldStart = now.minusDays(60);
        LocalDateTime oldExpiry = now.minusDays(30);
        LocalDateTime newStart = now.minusDays(1);
        insertAccount(USER_ID, 2000L, 4000L, 500L);
        jdbcTemplate.update("DELETE FROM bean_transaction WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("UPDATE bean_account SET total_expired = 1500 WHERE user_id = ?", USER_ID);
        insertMembership(1L, USER_ID, "1", oldStart, oldExpiry);
        jdbcTemplate.update("UPDATE user_membership SET expiry_settled_time = ? WHERE id = 1", oldExpiry);
        insertMembership(2L, USER_ID, "0", newStart,
                newStart.plusDays(MembershipPolicy.DURATION_DAYS));

        jdbcTemplate.update("""
                INSERT INTO bean_transaction
                    (user_id, type, change_value, balance_after, biz_no, source_biz_no, create_time)
                VALUES
                    (?, 'ISSUE', 2000, 2000, 'old-member-ledger', NULL, ?),
                    (?, 'DEDUCT', -500, 1500, 'old-late-task', NULL, ?),
                    (?, 'EXPIRE', -1500, 0, 'membership-expire:1', NULL, ?),
                    (?, 'ISSUE', 2000, 2000, 'new-member-ledger', NULL, ?)
                """, USER_ID, oldStart, USER_ID, oldStart.plusDays(1),
                USER_ID, oldExpiry, USER_ID, newStart);

        beanAccountService.refund(USER_ID, 500L, "old-late-task", "旧任务失败晚到退豆");
        long reExpired = membershipExpiryService.expirePostExpiryRefund(
                USER_ID, 500L, "old-late-task", "post-expiry-refund:old-late-task", "旧周期退豆清零");

        assertEquals(500L, reExpired);
        assertEquals(2000L, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID),
                "旧任务退豆不得混入新会员的2000豆");
        assertEquals("membership-expire:1|old-late-task", jdbcTemplate.queryForObject(
                "SELECT source_biz_no FROM bean_transaction WHERE biz_no = 'post-expiry-refund:old-late-task'",
                String.class));

        BeanRenewalRestoreResult restored = beanAccountService.restoreExpiredForRenewal(
                USER_ID, "membership-expire:1", "delayed-renew-order", "到期前支付的续费回调最后到达");
        BeanRefundRevokeResult revoked = beanAccountService.revokeForOrderRefund(
                USER_ID, 2000L, "old-member-ledger", "旧会员单退款");

        assertEquals(2000L, restored.restored(), "应恢复原到期1500+旧任务退豆500");
        assertEquals(2000L, revoked.revoked(), "RENEW_RESTORE 必须恢复原 ISSUE 批次来源");
        assertEquals(2000L, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID),
                "只保留新会员批次");
    }

    @Test
    void 真实Spring鉴权链与ControllerServiceMySQL串联_无Token401_有效Token触发到期清零() throws Exception {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(1);
        insertAccount(USER_ID, 3450L, 4050L, 600L);
        insertMembership(1L, USER_ID, "0", cutoff.minusDays(30), cutoff);

        mockMvc.perform(get("/api/mobile/bean/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
        assertEquals(3450L, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID),
                "未鉴权请求不得进入 Controller 触发结算");

        mockMvc.perform(get("/api/mobile/bean/balance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-mobile-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.balance").value(0))
                .andExpect(jsonPath("$.data.totalExpired").value(3450));

        assertEquals(0L, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(1L, number("SELECT COUNT(*) FROM bean_transaction WHERE user_id = ? AND type = 'EXPIRE'", USER_ID));
        assertEquals(1L, number("SELECT COUNT(*) FROM user_membership WHERE id = 1 AND expiry_settled_time IS NOT NULL"));
    }

    @Test
    void v34迁移把存量时长归一30天并用MySQLCheck阻止非30天() throws Exception {
        // 还原 V34 之前的三张表结构，直接执行生产迁移脚本。
        jdbcTemplate.execute("ALTER TABLE user_membership "
                + "DROP INDEX idx_status_settled_expire_user, "
                + "DROP INDEX idx_status_expire_user, "
                + "DROP COLUMN expiry_settled_time, "
                + "DROP COLUMN paid_time");
        jdbcTemplate.execute("ALTER TABLE bean_account DROP COLUMN total_revoked, DROP COLUMN total_expired");
        jdbcTemplate.execute("ALTER TABLE bean_transaction "
                + "DROP INDEX uk_type_source_biz, DROP INDEX idx_user_type_biz, DROP COLUMN source_biz_no");
        jdbcTemplate.execute("""
                CREATE TABLE membership_level (
                    level_id BIGINT NOT NULL AUTO_INCREMENT,
                    duration_days INT NOT NULL,
                    PRIMARY KEY (level_id)
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.update("INSERT INTO membership_level (duration_days) VALUES (99)");
        insertMembership(1L, USER_ID, "0",
                LocalDateTime.of(2026, 6, 10, 12, 0), LocalDateTime.of(2026, 7, 10, 12, 0));
        jdbcTemplate.update("UPDATE user_membership SET source_order_no = 'migration-paid-order' WHERE id = 1");
        LocalDateTime historicalPaidAt = LocalDateTime.of(2026, 6, 9, 9, 30);
        jdbcTemplate.update("INSERT INTO pay_order (order_no, pay_time, status) VALUES (?, ?, 1)",
                "migration-paid-order", historicalPaidAt);

        try (Connection connection = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V34__membership_expiry_cleanup.sql"));
        }

        assertEquals(30L, number("SELECT duration_days FROM membership_level WHERE level_id = 1"));
        assertEquals(1L, number("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'user_membership' "
                + "AND column_name = 'expiry_settled_time'"));
        assertEquals(1L, number("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'user_membership' "
                + "AND column_name = 'paid_time'"));
        assertEquals(historicalPaidAt, dateTime(
                "SELECT paid_time FROM user_membership WHERE source_order_no = 'migration-paid-order'"));
        assertEquals(1L, number("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'bean_account' "
                + "AND column_name = 'total_expired'"));
        assertEquals(1L, number("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'bean_account' "
                + "AND column_name = 'total_revoked'"));
        assertEquals(1L, number("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'bean_transaction' "
                + "AND column_name = 'source_biz_no'"));
        assertEquals(1L, number("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'user_membership' "
                + "AND index_name = 'idx_status_settled_expire_user'"));
        assertEquals(1L, number("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'bean_transaction' "
                + "AND index_name = 'uk_type_source_biz'"));
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("INSERT INTO membership_level (duration_days) VALUES (31)"));
    }

    @Test
    void v44迁移固定365天后_v47放开等级时长且不改历史权益() throws Exception {
        jdbcTemplate.execute("""
                CREATE TABLE membership_level (
                    level_id BIGINT NOT NULL AUTO_INCREMENT,
                    duration_days INT NOT NULL DEFAULT 30,
                    PRIMARY KEY (level_id),
                    CONSTRAINT chk_membership_duration_30 CHECK (duration_days = 30)
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.update("INSERT INTO membership_level (duration_days) VALUES (30)");
        jdbcTemplate.execute("ALTER TABLE pay_order "
                + "ADD COLUMN product_type VARCHAR(32) NULL, "
                + "ADD COLUMN snapshot_duration_days INT NULL");

        LocalDateTime firstStart = MembershipPolicy.now().minusDays(10);
        LocalDateTime oldFirstExpire = firstStart.plusDays(30);
        LocalDateTime oldSecondExpire = oldFirstExpire.plusDays(30);
        insertMembership(1L, USER_ID, "0", firstStart, oldFirstExpire);
        insertMembership(2L, USER_ID, "0", oldFirstExpire, oldSecondExpire);
        jdbcTemplate.update("UPDATE user_membership SET source_order_no = 'member-v44-a' WHERE id = 1");
        jdbcTemplate.update("UPDATE user_membership SET source_order_no = 'member-v44-b' WHERE id = 2");
        jdbcTemplate.update("INSERT INTO pay_order "
                        + "(order_no, status, product_type, snapshot_duration_days) VALUES (?, 1, 'MEMBERSHIP', 30)",
                "member-v44-a");
        jdbcTemplate.update("INSERT INTO pay_order "
                        + "(order_no, status, product_type, snapshot_duration_days) VALUES (?, 1, 'MEMBERSHIP', 30)",
                "member-v44-b");

        try (Connection connection = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V44__membership_365_and_permanent_recharge.sql"));
        }

        LocalDateTime firstExpire = firstStart.plusDays(MembershipPolicy.DURATION_DAYS);
        assertEquals(365L, number("SELECT duration_days FROM membership_level WHERE level_id = 1"));
        assertEquals(firstStart, dateTime("SELECT start_time FROM user_membership WHERE id = 1"));
        assertEquals(firstExpire, dateTime("SELECT expire_time FROM user_membership WHERE id = 1"));
        assertEquals(firstExpire, dateTime("SELECT start_time FROM user_membership WHERE id = 2"));
        assertEquals(firstExpire.plusDays(MembershipPolicy.DURATION_DAYS),
                dateTime("SELECT expire_time FROM user_membership WHERE id = 2"));
        assertEquals(2L, number("SELECT COUNT(*) FROM pay_order WHERE snapshot_duration_days = 365"));
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("INSERT INTO membership_level (duration_days) VALUES (30)"));

        try (Connection connection = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V47__membership_editable_duration.sql"));
        }

        jdbcTemplate.update("UPDATE membership_level SET duration_days = 30 WHERE level_id = 1");
        assertEquals(30L, number("SELECT duration_days FROM membership_level WHERE level_id = 1"));
        assertEquals(firstExpire, dateTime("SELECT expire_time FROM user_membership WHERE id = 1"));
        assertEquals(firstExpire.plusDays(MembershipPolicy.DURATION_DAYS),
                dateTime("SELECT expire_time FROM user_membership WHERE id = 2"));
        assertEquals(2L, number("SELECT COUNT(*) FROM pay_order WHERE snapshot_duration_days = 365"));
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("INSERT INTO membership_level (duration_days) VALUES (0)"));
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("INSERT INTO membership_level (duration_days) VALUES (3651)"));
    }

    private void insertAccount(long userId, long balance, long totalEarned, long totalConsumed) {
        jdbcTemplate.update("""
                        INSERT INTO bean_account
                            (user_id, balance, total_earned, total_consumed, total_expired, create_time, update_time)
                        VALUES (?, ?, ?, ?, 0, NOW(), NOW())
                        """,
                userId, balance, totalEarned, totalConsumed);
        long credited = balance + totalConsumed;
        if (credited > 0) {
            jdbcTemplate.update("""
                            INSERT INTO bean_transaction
                                (user_id, type, change_value, balance_after, biz_no, create_time)
                            VALUES (?, 'ISSUE', ?, ?, ?, DATE_SUB(NOW(), INTERVAL 2 SECOND))
                            """,
                    userId, credited, credited, "seed-membership-" + userId);
        }
        if (totalConsumed > 0) {
            jdbcTemplate.update("""
                            INSERT INTO bean_transaction
                                (user_id, type, change_value, balance_after, biz_no, create_time)
                            VALUES (?, 'DEDUCT', ?, ?, ?, DATE_SUB(NOW(), INTERVAL 1 SECOND))
                            """,
                    userId, -totalConsumed, balance, "seed-consume-" + userId);
        }
    }

    private void insertMembership(long id, long userId, String status,
                                  LocalDateTime startTime, LocalDateTime expireTime) {
        jdbcTemplate.update("""
                        INSERT INTO user_membership
                            (id, user_id, level_id, level_sort, level_name, snapshot_grant_beans,
                             snapshot_price, source, start_time, expire_time, status, create_time)
                        VALUES (?, ?, 1, 1, '青铜会员', 2000, 1.99, 'PAY', ?, ?, ?, NOW())
                        """,
                id, userId, startTime, expireTime, status);
    }

    private long number(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0L : value.longValue();
    }

    private LocalDateTime dateTime(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, LocalDateTime.class, args);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = BeanAccountMapper.class)
    @Import({BeanAccountServiceImpl.class, BeanTransactionServiceImpl.class,
            MembershipExpiryServiceImpl.class, UserMembershipServiceImpl.class,
            MobileBeanController.class, TestWebConfig.class})
    static class TestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestWebConfig {

        @Bean
        IBeanRechargePackageService beanRechargePackageService() {
            return mock(IBeanRechargePackageService.class);
        }

        @Bean
        IMobileUserService mobileUserService() {
            IMobileUserService service = mock(IMobileUserService.class);
            MobileUser user = new MobileUser();
            user.setUserId(USER_ID);
            when(service.getById(USER_ID)).thenReturn(user);
            return service;
        }

        @Bean
        MobileTokenService mobileTokenService() {
            MobileTokenService service = mock(MobileTokenService.class);
            when(service.getUserId("valid-mobile-token")).thenReturn(USER_ID);
            return service;
        }

        @Bean
        MobileJwtFilter mobileJwtFilter(MobileTokenService mobileTokenService) {
            return new MobileJwtFilter(mobileTokenService);
        }

        @Bean
        MobileAuthEntryPoint mobileAuthEntryPoint() {
            return new MobileAuthEntryPoint();
        }

        @Bean
        SecurityFilterChain testMobileSecurityChain(
                HttpSecurity http, MobileJwtFilter mobileJwtFilter,
                MobileAuthEntryPoint mobileAuthEntryPoint) throws Exception {
            http.securityMatcher("/api/mobile/**")
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .exceptionHandling(ex -> ex.authenticationEntryPoint(mobileAuthEntryPoint))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
            http.addFilterBefore(mobileJwtFilter, UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }
    }
}
