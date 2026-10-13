package com.aurora.admin.mobile.service;

import com.aurora.admin.distribution.service.IDistributionFinanceService;
import com.aurora.admin.mobile.mapper.PayOrderMapper;
import com.aurora.admin.mobile.service.impl.BeanAccountServiceImpl;
import com.aurora.admin.mobile.service.impl.MembershipRefundEntitlementServiceImpl;
import com.aurora.admin.mobile.service.impl.PayOrderBizServiceImpl;
import com.aurora.admin.mobile.service.impl.PayOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * 真实 MySQL 集成验证：微信虚拟支付退款通知、订单状态、会员段与豆子回收必须在同一事务中一致完成。
 */
@SpringBootTest(
        classes = VirtualRefundEntitlementMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=false",
                "spring.task.scheduling.enabled=false"
        })
@Testcontainers(disabledWithoutDocker = true)
class VirtualRefundEntitlementMySqlTest {

    private static final long USER_ID = 1001L;
    private static final LocalDateTime REFUND_AT = LocalDateTime.of(2026, 7, 10, 12, 0);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("aurora_virtual_refund_test")
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
    private IPayOrderBizService payOrderBizService;

    @Autowired
    private IMembershipRefundEntitlementService membershipRefundEntitlementService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS pay_order");
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
                    source_biz_no VARCHAR(96) NULL,
                    remark VARCHAR(255) NULL,
                    create_time DATETIME NULL,
                    PRIMARY KEY (id),
                    KEY idx_user_create (user_id, create_time),
                    KEY idx_user_refund_revoke (user_id, type, biz_no)
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
                    KEY idx_user_status (user_id, status)
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.execute("""
                CREATE TABLE pay_order (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    order_no VARCHAR(64) NOT NULL,
                    user_id BIGINT NULL,
                    open_id VARCHAR(64) NULL,
                    amount INT NOT NULL,
                    subject VARCHAR(255) NULL,
                    product_type VARCHAR(16) NULL,
                    product_id BIGINT NULL,
                    product_name VARCHAR(255) NULL,
                    pay_channel VARCHAR(16) NOT NULL DEFAULT 'WXPAY',
                    wx_order_id VARCHAR(64) NULL,
                    env TINYINT NULL,
                    virtual_product_id VARCHAR(20) NULL,
                    snapshot_beans BIGINT NULL,
                    snapshot_duration_days INT NULL,
                    snapshot_grant_beans BIGINT NULL,
                    snapshot_level_sort INT NULL,
                    fulfillment_status TINYINT NULL,
                    fulfillment_error VARCHAR(500) NULL,
                    fulfilled_at DATETIME NULL,
                    delivery_confirmed TINYINT NOT NULL DEFAULT 0,
                    delivery_confirmed_at DATETIME NULL,
                    status TINYINT NOT NULL DEFAULT 0,
                    transaction_id VARCHAR(64) NULL,
                    pay_time DATETIME NULL,
                    refund_no VARCHAR(64) NULL,
                    refund_amount INT NULL,
                    refund_time DATETIME NULL,
                    create_time DATETIME NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_order_no (order_no),
                    UNIQUE KEY uk_wx_order_id (wx_order_id)
                ) ENGINE=InnoDB
                """);
    }

    @Test
    void 最后一个会员退款_仅撤销该会员赠豆并保留加油包() {
        String memberOrder = "member-refund-last";
        insertAccount(2500L, 2500L, 0L);
        insertTransaction("ISSUE", 2000L, 2000L, memberOrder);
        insertTransaction("RECHARGE", 500L, 2500L, "addon-before-refund");
        insertMembership(memberOrder, 2000L,
                REFUND_AT.minusDays(10), REFUND_AT.plusDays(20), "0");
        insertPaidOrder(memberOrder, 199, "MEMBERSHIP", null, 2000L, 1, 1);

        payOrderBizService.handleVirtualRefund(memberOrder, "refund-last-1", 199, REFUND_AT);

        assertEquals(2L, number("SELECT status FROM pay_order WHERE order_no = ?", memberOrder));
        assertEquals(2L, number("SELECT fulfillment_status FROM pay_order WHERE order_no = ?", memberOrder));
        assertEquals("2", textValue("SELECT status FROM user_membership WHERE source_order_no = ?", memberOrder));
        assertEquals(500L, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(2000L, number("SELECT total_revoked FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(1L, number("SELECT COUNT(*) FROM bean_transaction WHERE type = 'REVOKE' AND biz_no = ?",
                memberOrder));
        assertEquals(-2000L, number("SELECT change_value FROM bean_transaction WHERE type = 'REVOKE' AND biz_no = ?",
                memberOrder));
        assertEquals("TARGET:" + memberOrder, textValue(
                "SELECT source_biz_no FROM bean_transaction WHERE type = 'REVOKE' AND biz_no = ?", memberOrder));
        assertEquals("refund-last-1", textValue("SELECT refund_no FROM pay_order WHERE order_no = ?", memberOrder));
        assertEquals(199L, number("SELECT refund_amount FROM pay_order WHERE order_no = ?", memberOrder));
    }

    @Test
    void 加油包部分已消费_只回收该订单剩余且不误扣后来会员豆() {
        String addOnOrder = "addon-refund-partial";
        String laterMemberOrder = "member-paid-later";
        insertAccount(2800L, 4000L, 1200L);
        insertTransaction("RECHARGE", 2000L, 2000L, addOnOrder);
        insertTransaction("DEDUCT", -1200L, 800L, "generation-before-member");
        insertTransaction("ISSUE", 2000L, 2800L, laterMemberOrder);
        insertMembership(laterMemberOrder, 2000L,
                REFUND_AT.minusDays(1), REFUND_AT.plusDays(29), "0");
        insertPaidOrder(addOnOrder, 299, "BEAN_RECHARGE", 2000L, null, 1, 1);

        payOrderBizService.handleVirtualRefund(addOnOrder, "refund-addon-partial", 299, REFUND_AT);

        assertEquals(2L, number("SELECT status FROM pay_order WHERE order_no = ?", addOnOrder));
        assertEquals(2000L, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(800L, number("SELECT total_revoked FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(1200L, number("SELECT total_consumed FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(-800L, number("SELECT change_value FROM bean_transaction WHERE type = 'REVOKE' AND biz_no = ?",
                addOnOrder));
        assertEquals("TARGET:" + addOnOrder, textValue(
                "SELECT source_biz_no FROM bean_transaction WHERE type = 'REVOKE' AND biz_no = ?", addOnOrder));
        assertEquals(1L, number("SELECT COUNT(*) FROM bean_transaction WHERE type = 'ISSUE' AND biz_no = ?",
                laterMemberOrder));
        assertEquals("0", textValue("SELECT status FROM user_membership WHERE source_order_no = ?", laterMemberOrder));
    }

    @Test
    void 同一退款通知重复到达_订单与REVOKE流水均幂等() {
        String addOnOrder = "addon-refund-replay";
        insertAccount(1000L, 1000L, 0L);
        insertTransaction("RECHARGE", 1000L, 1000L, addOnOrder);
        insertMembership("active-member-for-replay", 2000L,
                REFUND_AT.minusDays(2), REFUND_AT.plusDays(28), "0");
        insertPaidOrder(addOnOrder, 299, "BEAN_RECHARGE", 1000L, null, 1, 1);

        payOrderBizService.handleVirtualRefund(addOnOrder, "refund-replay-1", 299, REFUND_AT);
        payOrderBizService.handleVirtualRefund(addOnOrder, "refund-replay-1", 299, REFUND_AT.plusSeconds(1));

        assertEquals(2L, number("SELECT status FROM pay_order WHERE order_no = ?", addOnOrder));
        assertEquals(0L, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(1000L, number("SELECT total_revoked FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(1L, number("SELECT COUNT(*) FROM bean_transaction WHERE type = 'REVOKE' AND biz_no = ?",
                addOnOrder));
        assertEquals("refund-replay-1", textValue("SELECT refund_no FROM pay_order WHERE order_no = ?", addOnOrder));
    }

    @Test
    void status4未发权益订单退款_只收敛订单且不回收任何豆子() {
        String pendingOrder = "addon-paid-pending-refund";
        insertAccount(777L, 777L, 0L);
        insertTransaction("GIFT", 777L, 777L, "unrelated-gift");
        insertPaidOrder(pendingOrder, 299, "BEAN_RECHARGE", 2000L, null, 4, 2);

        payOrderBizService.handleVirtualRefund(pendingOrder, "refund-pending-1", 299, REFUND_AT);

        assertEquals(2L, number("SELECT status FROM pay_order WHERE order_no = ?", pendingOrder));
        assertEquals(777L, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(0L, number("SELECT total_revoked FROM bean_account WHERE user_id = ?", USER_ID));
        assertEquals(0L, number("SELECT COUNT(*) FROM bean_transaction WHERE type = 'REVOKE'"));
        assertEquals(1L, number("SELECT COUNT(*) FROM bean_transaction WHERE type = 'GIFT' AND biz_no = 'unrelated-gift'"));
        assertEquals(2L, number("SELECT fulfillment_status FROM pay_order WHERE order_no = ?", pendingOrder));
        assertEquals("订单已退款，未发放权益", textValue(
                "SELECT fulfillment_error FROM pay_order WHERE order_no = ?", pendingOrder));
    }

    @Test
    void 延迟退款通知_历史退款时间修正链但按处理时间保留新购会员() {
        LocalDateTime refundAt = LocalDateTime.of(2026, 7, 2, 10, 0);
        LocalDateTime processedAt = LocalDateTime.of(2026, 7, 10, 10, 0);
        LocalDateTime newPaidAt = LocalDateTime.of(2026, 7, 5, 9, 30);
        String refundedOrder = "member-refund-delayed";
        String newOrder = "member-new-after-refund";
        LocalDateTime oldExpire = LocalDateTime.of(2026, 8, 1, 10, 0);

        insertAccount(4000L, 4000L, 0L);
        insertMembership(refundedOrder, 2000L,
                refundAt.minusDays(20), oldExpire, "0");
        insertMembership(newOrder, 2000L,
                oldExpire, oldExpire.plusDays(MembershipPolicy.DURATION_DAYS), "0", newPaidAt);
        insertPaidOrderAt(refundedOrder, 199, "MEMBERSHIP", null, 2000L,
                1, 1, refundAt.minusDays(20));
        insertPaidOrderAt(newOrder, 199, "MEMBERSHIP", null, 2000L,
                1, 1, newPaidAt);

        MembershipRefundEntitlementResult result = membershipRefundEntitlementService
                .cancelForRefund(USER_ID, refundedOrder, refundAt, processedAt);

        assertEquals(true, result.hasActiveMembershipAfter());
        assertEquals("2", textValue(
                "SELECT status FROM user_membership WHERE source_order_no = ?", refundedOrder));
        assertEquals(newPaidAt, dateTimeValue(
                "SELECT start_time FROM user_membership WHERE source_order_no = ?", newOrder));
        assertEquals(newPaidAt.plusDays(MembershipPolicy.DURATION_DAYS), dateTimeValue(
                "SELECT expire_time FROM user_membership WHERE source_order_no = ?", newOrder));
        assertEquals(4000L, number("SELECT balance FROM bean_account WHERE user_id = ?", USER_ID));
    }

    private void insertAccount(long balance, long totalEarned, long totalConsumed) {
        jdbcTemplate.update("""
                        INSERT INTO bean_account
                            (user_id, balance, total_earned, total_consumed, total_expired,
                             total_revoked, create_time, update_time)
                        VALUES (?, ?, ?, ?, 0, 0, NOW(), NOW())
                        """,
                USER_ID, balance, totalEarned, totalConsumed);
    }

    private void insertTransaction(String type, long changeValue, long balanceAfter, String bizNo) {
        jdbcTemplate.update("""
                        INSERT INTO bean_transaction
                            (user_id, type, change_value, balance_after, biz_no, create_time)
                        VALUES (?, ?, ?, ?, ?, NOW())
                        """,
                USER_ID, type, changeValue, balanceAfter, bizNo);
    }

    private void insertMembership(String sourceOrderNo, long grantBeans,
                                  LocalDateTime startTime, LocalDateTime expireTime, String status) {
        insertMembership(sourceOrderNo, grantBeans, startTime, expireTime, status, startTime);
    }

    private void insertMembership(String sourceOrderNo, long grantBeans,
                                  LocalDateTime startTime, LocalDateTime expireTime, String status,
                                  LocalDateTime paidTime) {
        jdbcTemplate.update("""
                        INSERT INTO user_membership
                            (user_id, level_id, level_sort, level_name, snapshot_grant_beans,
                             snapshot_price, source, source_order_no, paid_time, start_time,
                             expire_time, status, create_time)
                        VALUES (?, 1, 1, '青铜会员', ?, 1.99, 'PAY', ?, ?, ?, ?, ?, NOW())
                        """,
                USER_ID, grantBeans, sourceOrderNo, paidTime, startTime, expireTime, status);
    }

    private void insertPaidOrder(String orderNo, int amount, String productType,
                                 Long snapshotBeans, Long snapshotGrantBeans,
                                 int status, int fulfillmentStatus) {
        insertPaidOrderAt(orderNo, amount, productType, snapshotBeans, snapshotGrantBeans,
                status, fulfillmentStatus, REFUND_AT.minusMinutes(10));
    }

    private void insertPaidOrderAt(String orderNo, int amount, String productType,
                                   Long snapshotBeans, Long snapshotGrantBeans,
                                   int status, int fulfillmentStatus,
                                   LocalDateTime paidAt) {
        jdbcTemplate.update("""
                        INSERT INTO pay_order
                            (order_no, user_id, open_id, amount, subject, product_type, product_id,
                             product_name, pay_channel, snapshot_beans, snapshot_duration_days,
                             snapshot_grant_beans, snapshot_level_sort, fulfillment_status,
                             delivery_confirmed, status, transaction_id, pay_time, create_time)
                        VALUES (?, ?, 'openid-1', ?, ?, ?, 1, ?, 'VIRTUAL', ?, 30, ?, 1, ?,
                                1, ?, 'transaction-1', ?, NOW())
                        """,
                orderNo, USER_ID, amount, productType, productType, productType, snapshotBeans,
                snapshotGrantBeans, fulfillmentStatus, status, paidAt);
    }

    private long number(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0L : value.longValue();
    }

    private String textValue(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, String.class, args);
    }

    private LocalDateTime dateTimeValue(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, LocalDateTime.class, args);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = PayOrderMapper.class)
    @Import({
            PayOrderServiceImpl.class,
            BeanAccountServiceImpl.class,
            MembershipRefundEntitlementServiceImpl.class,
            PayOrderBizServiceImpl.class,
            MockDependencies.class
    })
    static class TestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MockDependencies {

        @Bean
        IDistributionFinanceService distributionFinanceService() {
            return mock(IDistributionFinanceService.class);
        }

        @Bean
        WxServiceFactory wxServiceFactory() {
            return mock(WxServiceFactory.class);
        }

        @Bean
        IMobileUserService mobileUserService() {
            return mock(IMobileUserService.class);
        }

        @Bean
        IUserMembershipService userMembershipService() {
            return mock(IUserMembershipService.class);
        }

        @Bean
        IMembershipLevelService membershipLevelService() {
            return mock(IMembershipLevelService.class);
        }

        @Bean
        IBeanRechargePackageService beanRechargePackageService() {
            return mock(IBeanRechargePackageService.class);
        }

        @Bean
        ISysWxConfigService sysWxConfigService() {
            return mock(ISysWxConfigService.class);
        }
    }
}
