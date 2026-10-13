package com.aurora.admin.mobile.service;

import com.aurora.admin.distribution.service.IDistributionFinanceService;
import com.aurora.admin.mobile.exception.MembershipRequiredException;
import com.aurora.admin.mobile.mapper.PayOrderMapper;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 真实 MySQL 事务验证：支付期间会员到期时，已扣款订单必须提交为待退款终态。 */
@SpringBootTest(
        classes = PayOrderPendingRefundMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.flyway.enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class PayOrderPendingRefundMySqlTest {

    private static final String ORDER_NO = "20260710000000000099";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("aurora_pay_refund_test")
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
    private IUserMembershipService userMembershipService;

    @Autowired
    private IBeanAccountService beanAccountService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS pay_order");
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
        jdbcTemplate.update("""
                INSERT INTO pay_order
                    (order_no, user_id, open_id, amount, subject, product_type, product_id,
                     product_name, pay_channel, snapshot_beans, fulfillment_status,
                     delivery_confirmed, status, create_time)
                VALUES (?, 1001, 'openid-1', 299, '加油包', 'BEAN_RECHARGE', 2,
                        '加油包', 'VIRTUAL', 2000, 0, 0, 0, NOW())
                """, ORDER_NO);
    }

    @Test
    void membershipExpiresDuringPayment_commitsPaidPendingRefundAndNeverRecharges() {
        doAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                    "资格二次校验必须处于支付回调事务中");
            throw new MembershipRequiredException("加油包仅限有效会员购买");
        }).when(userMembershipService).requireActiveMembership(1001L);

        payOrderBizService.handleVirtualPaySuccess(ORDER_NO, "wx-order-1", "transaction-1", 299);

        assertEquals(4, number("SELECT status FROM pay_order WHERE order_no = ?", ORDER_NO));
        assertEquals(2, number("SELECT fulfillment_status FROM pay_order WHERE order_no = ?", ORDER_NO));
        assertEquals("支付完成时会员已到期，未发放加油包，请人工退款",
                jdbcTemplate.queryForObject(
                        "SELECT fulfillment_error FROM pay_order WHERE order_no = ?", String.class, ORDER_NO));
        assertEquals("transaction-1", jdbcTemplate.queryForObject(
                "SELECT transaction_id FROM pay_order WHERE order_no = ?", String.class, ORDER_NO));
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT pay_time FROM pay_order WHERE order_no = ?", java.sql.Timestamp.class, ORDER_NO));
        verify(beanAccountService, never()).recharge(anyLong(), anyLong(), anyString(), anyString());

        var view = payOrderBizService.queryOrder(ORDER_NO, false, 1001L);
        assertEquals(4, view.getStatus());
        assertEquals("已支付待退款", view.getStatusText());
        assertEquals(2, view.getFulfillmentStatus());
        assertEquals("支付完成时会员已到期，未发放加油包，请人工退款", view.getFulfillmentError());

        // 晚到重复回调命中 status=4 幂等短路，不重复资格校验或履约。
        payOrderBizService.handleVirtualPaySuccess(ORDER_NO, "wx-order-1", "transaction-1", 299);
        verify(userMembershipService).requireActiveMembership(1001L);
    }

    private long number(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0L : value.longValue();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = PayOrderMapper.class)
    @Import({PayOrderServiceImpl.class, PayOrderBizServiceImpl.class, MockDependencies.class})
    static class TestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MockDependencies {

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
        IBeanAccountService beanAccountService() {
            return mock(IBeanAccountService.class);
        }

        @Bean
        IMembershipLevelService membershipLevelService() {
            return mock(IMembershipLevelService.class);
        }

        @Bean
        IMembershipRefundEntitlementService membershipRefundEntitlementService() {
            return mock(IMembershipRefundEntitlementService.class);
        }

        @Bean
        IBeanRechargePackageService beanRechargePackageService() {
            return mock(IBeanRechargePackageService.class);
        }

        @Bean
        ISysWxConfigService sysWxConfigService() {
            return mock(ISysWxConfigService.class);
        }

        @Bean
        IDistributionFinanceService distributionFinanceService() {
            return mock(IDistributionFinanceService.class);
        }
    }
}
