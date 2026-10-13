package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.BeanAccount;
import com.aurora.admin.mobile.domain.BeanTransaction;
import com.aurora.admin.mobile.mapper.BeanAccountMapper;
import com.aurora.admin.mobile.mapper.BeanTransactionMapper;
import com.aurora.admin.mobile.service.impl.BeanAccountServiceImpl;
import com.aurora.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BeanAccountService 单测:原子扣减成功 / 余额不足抛错 / 退还 / 多线程并发 deduct 不超扣。
 * 用内存版 Mapper(InMemoryAccountMapper)精确模拟数据库 deductIfEnough 的原子
 * 「WHERE balance>=amount 才更新」语义(synchronized 保证条件判断+扣减原子),
 * 在无数据库/无 Spring 容器下验证 Service 层编排不超扣、流水与余额一致。
 */
class BeanAccountServiceTest {

    private static final long USER_ID = 1001L;

    private BeanAccountServiceImpl service;
    private InMemoryAccountMapper accountMapper;
    private RecordingTransactionMapper transactionMapper;

    @BeforeEach
    void setUp() throws Exception {
        accountMapper = new InMemoryAccountMapper();
        transactionMapper = new RecordingTransactionMapper();
        service = new BeanAccountServiceImpl(transactionMapper);
        // ServiceImpl 的 baseMapper 由 Spring 注入,单测用反射注入内存版 mapper
        injectBaseMapper(service, accountMapper);
    }

    @Test
    void 扣减成功并记流水且余额快照一致() {
        accountMapper.seed(USER_ID, 100L);

        service.deduct(USER_ID, 30L, "biz-1", "扣费");

        assertEquals(70L, accountMapper.balanceOf(USER_ID), "余额应为 100-30=70");
        assertEquals(30L, accountMapper.consumedOf(USER_ID), "累计消耗应为 30");
        assertEquals(1, transactionMapper.records.size(), "应有一条流水");
        BeanTransaction tx = transactionMapper.records.get(0);
        assertEquals("DEDUCT", tx.getType());
        assertEquals(-30L, tx.getChangeValue(), "扣费流水变动值为负");
        assertEquals(70L, tx.getBalanceAfter(), "流水余额快照与账户一致");
    }

    @Test
    void 余额不足扣减抛业务异常且不记流水() {
        accountMapper.seed(USER_ID, 20L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.deduct(USER_ID, 50L, "biz-2", "扣费"));
        assertEquals("豆子余额不足", ex.getMessage());

        assertEquals(20L, accountMapper.balanceOf(USER_ID), "余额不应变化");
        assertEquals(0, transactionMapper.records.size(), "余额不足时不应记流水");
    }

    @Test
    void 退还增加余额并扣减累计消耗记流水() {
        // 先扣 60 再退 40,验证 refund 加余额、减 total_consumed(不为负)
        accountMapper.seed(USER_ID, 100L);
        service.deduct(USER_ID, 60L, null, "扣费");

        service.refund(USER_ID, 40L, "biz-3", "退还");

        assertEquals(80L, accountMapper.balanceOf(USER_ID), "退还后余额应为 40+40=80");
        assertEquals(20L, accountMapper.consumedOf(USER_ID), "累计消耗应为 60-40=20");
        BeanTransaction last = transactionMapper.records.get(transactionMapper.records.size() - 1);
        assertEquals("REFUND", last.getType());
        assertEquals(40L, last.getChangeValue(), "退还流水变动值为正");
        assertEquals(80L, last.getBalanceAfter());
    }

    @Test
    void 退还不会使累计消耗变为负数() {
        accountMapper.seed(USER_ID, 100L);
        service.deduct(USER_ID, 10L, null, "扣费");

        // 退还量大于已消耗,total_consumed 应被夹到 0 而非负
        service.refund(USER_ID, 50L, null, "退还");

        assertEquals(0L, accountMapper.consumedOf(USER_ID), "累计消耗应被夹到 0");
    }

    @Test
    void 相同业务号重复扣减和退款均保持幂等() {
        accountMapper.seed(USER_ID, 100L);

        service.deduct(USER_ID, 30L, "img:task-1", "生成预扣");
        service.deduct(USER_ID, 30L, "img:task-1", "生成预扣重试");
        service.refund(USER_ID, 30L, "img:task-1", "生成失败退还");
        service.refund(USER_ID, 30L, "img:task-1", "生成失败退还重试");

        assertEquals(100L, accountMapper.balanceOf(USER_ID));
        assertEquals(0L, accountMapper.consumedOf(USER_ID));
        assertEquals(2, transactionMapper.records.size(), "相同业务号只能各记一条扣减和退款流水");
    }

    @Test
    void 相同业务号不同金额应拒绝避免账务冲突() {
        accountMapper.seed(USER_ID, 100L);
        service.deduct(USER_ID, 30L, "img:task-2", "生成预扣");

        ServiceException deductError = assertThrows(ServiceException.class,
                () -> service.deduct(USER_ID, 20L, "img:task-2", "错误重试"));
        assertEquals("业务单号重复且扣豆数量不一致", deductError.getMessage());

        service.refund(USER_ID, 30L, "img:task-2", "生成失败退还");
        ServiceException refundError = assertThrows(ServiceException.class,
                () -> service.refund(USER_ID, 20L, "img:task-2", "错误退款重试"));
        assertEquals("业务单号重复且退豆数量不一致", refundError.getMessage());
        assertEquals(100L, accountMapper.balanceOf(USER_ID));
    }

    @Test
    void 消费优先使用会员赠豆_到期仅失效会员剩余并保留加油包() {
        accountMapper.seed(USER_ID, 0L);
        service.recharge(USER_ID, 1000L, "permanent-addon", "永久加油包");
        service.issue(USER_ID, 2000L, "membership-order", "会员赠豆");
        service.deduct(USER_ID, 500L, "generation-before-expiry", "生成扣豆");

        long expired = service.expireMembershipGrantBalance(
                USER_ID, Long.MAX_VALUE, "membership-expire:100", null, "会员到期");

        assertEquals(1500L, expired, "500豆消费应优先落在会员赠豆批次");
        assertEquals(1000L, accountMapper.balanceOf(USER_ID), "加油包豆应永久保留");
        assertEquals(1500L, accountMapper.expiredOf(USER_ID));
        BeanTransaction expiry = transactionMapper.records.get(transactionMapper.records.size() - 1);
        assertEquals("EXPIRE", expiry.getType());
        assertEquals(-1500L, expiry.getChangeValue());
    }

    @Test
    void 账户只有加油包豆时会员到期不产生失效流水() {
        accountMapper.seed(USER_ID, 0L);
        service.recharge(USER_ID, 1000L, "addon-only", "永久加油包");

        long expired = service.expireMembershipGrantBalance(
                USER_ID, Long.MAX_VALUE, "membership-expire:101", null, "会员到期");

        assertEquals(0L, expired);
        assertEquals(1000L, accountMapper.balanceOf(USER_ID));
        assertEquals(1, transactionMapper.records.size(), "只有原加油包充值流水，不应新增EXPIRE流水");
    }

    @Test
    void 退款回收指定豆子并单独累计不计入消费() {
        accountMapper.seed(USER_ID, 0L);
        service.issue(USER_ID, 2000L, "refund-order-1", "会员赠豆");
        service.grant(USER_ID, 1000L, "unrelated-gift", "其他赠豆");

        BeanRefundRevokeResult result = service.revokeForOrderRefund(
                USER_ID, 2000L, "refund-order-1", "会员订单全额退款");

        assertEquals(2000L, result.requested());
        assertEquals(2000L, result.revoked());
        assertEquals(0L, result.shortfall());
        assertEquals(1000L, result.balanceAfter());
        assertEquals(1000L, accountMapper.balanceOf(USER_ID));
        assertEquals(2000L, accountMapper.revokedOf(USER_ID));
        assertEquals(0L, accountMapper.consumedOf(USER_ID), "权益回收不应冒充真实消费");
        BeanTransaction tx = transactionMapper.records.get(transactionMapper.records.size() - 1);
        assertEquals("REVOKE", tx.getType());
        assertEquals(-2000L, tx.getChangeValue());
        assertEquals(1000L, tx.getBalanceAfter());
        assertTrue(tx.getRemark().contains("未回收=0"));
    }

    @Test
    void 退款回收余额不足不为负且重复通知不误扣新豆子() {
        accountMapper.seed(USER_ID, 0L);
        service.recharge(USER_ID, 2000L, "refund-order-2", "加油包");
        service.deduct(USER_ID, 1500L, "used-before-refund", "已使用");

        BeanRefundRevokeResult first = service.revokeForOrderRefund(
                USER_ID, 2000L, "refund-order-2", "加油包全额退款");
        service.grant(USER_ID, 800L, "later-gift", "退款后的新赠送");
        BeanRefundRevokeResult replay = service.revokeForOrderRefund(
                USER_ID, 2000L, "refund-order-2", "重复通知");

        assertEquals(500L, first.revoked());
        assertEquals(1500L, first.shortfall());
        assertEquals(0L, first.balanceAfter());
        assertEquals(800L, accountMapper.balanceOf(USER_ID), "重复退款通知不得回收后续新获得的豆子");
        assertTrue(replay.alreadyProcessed());
        assertEquals(500L, replay.revoked());
        assertEquals(4, transactionMapper.records.size(), "重复通知不得新增第二条 REVOKE");
    }

    @Test
    void 缺少原始订单发放流水时保守不扣其他来源豆子() {
        accountMapper.seed(USER_ID, 1000L);

        BeanRefundRevokeResult result = service.revokeForOrderRefund(
                USER_ID, 2000L, "legacy-order-without-ledger", "存量异常订单退款");

        assertEquals(0L, result.revoked());
        assertEquals(2000L, result.shortfall());
        assertEquals(1000L, accountMapper.balanceOf(USER_ID));
    }

    @Test
    void 旧加油包已消费完_首次退款也绝不扣后来会员豆() {
        accountMapper.seed(USER_ID, 0L);
        service.recharge(USER_ID, 2000L, "old-addon-order", "旧加油包");
        service.deduct(USER_ID, 2000L, "generation-1", "已全部使用");
        service.issue(USER_ID, 2000L, "new-membership-order", "后来新订会员赠豆");

        BeanRefundRevokeResult result = service.revokeForOrderRefund(
                USER_ID, 2000L, "old-addon-order", "旧加油包退款");

        assertEquals(0L, result.revoked());
        assertEquals(2000L, result.shortfall());
        assertEquals(2000L, accountMapper.balanceOf(USER_ID), "后来会员豆必须完整保留");
        BeanTransaction revoke = transactionMapper.records.get(transactionMapper.records.size() - 1);
        assertEquals("REVOKE", revoke.getType());
        assertEquals(0L, revoke.getChangeValue());
        assertEquals("TARGET:old-addon-order", revoke.getSourceBizNo());
    }

    @Test
    void 旧加油包部分消费_仅回收FIFO剩余并保留后来会员豆() {
        accountMapper.seed(USER_ID, 0L);
        service.recharge(USER_ID, 2000L, "partial-addon-order", "旧加油包");
        service.deduct(USER_ID, 1200L, "generation-2", "部分使用");
        service.issue(USER_ID, 2000L, "later-member-order", "后来会员赠豆");

        BeanRefundRevokeResult result = service.revokeForOrderRefund(
                USER_ID, 2000L, "partial-addon-order", "旧加油包退款");

        assertEquals(800L, result.revoked());
        assertEquals(1200L, result.shortfall());
        assertEquals(2000L, accountMapper.balanceOf(USER_ID));
        assertEquals(800L, accountMapper.revokedOf(USER_ID));
    }

    @Test
    void 生成失败退豆恢复原扣减批次_支付退款仍能完整撤销该订单豆() {
        accountMapper.seed(USER_ID, 0L);
        service.recharge(USER_ID, 2000L, "addon-with-task-refund", "加油包");
        service.deduct(USER_ID, 500L, "failed-task-1", "生成扣豆");
        service.refund(USER_ID, 500L, "failed-task-1", "生成失败退豆");
        service.issue(USER_ID, 2000L, "later-member-after-task", "后来会员赠豆");

        BeanRefundRevokeResult result = service.revokeForOrderRefund(
                USER_ID, 2000L, "addon-with-task-refund", "加油包退款");

        assertEquals(2000L, result.revoked());
        assertEquals(0L, result.shortfall());
        assertEquals(2000L, accountMapper.balanceOf(USER_ID), "只保留后来会员豆");
    }

    @Test
    void 会员退款后无其他会员时可回收全部余额() {
        accountMapper.seed(USER_ID, 3450L);

        BeanRefundRevokeResult result = service.revokeAllForOrderRefund(
                USER_ID, "refund-member-all", "会员取消后整户回收");

        assertEquals(3450L, result.requested());
        assertEquals(3450L, result.revoked());
        assertEquals(0L, accountMapper.balanceOf(USER_ID));
        assertEquals(3450L, accountMapper.revokedOf(USER_ID));
    }

    @Test
    void 到期前付款但回调晚到时恢复原清零豆子并幂等() {
        accountMapper.seed(USER_ID, 0L);
        accountMapper.setExpired(USER_ID, 3450L);
        BeanTransaction expiry = new BeanTransaction();
        expiry.setUserId(USER_ID);
        expiry.setType("EXPIRE");
        expiry.setChangeValue(-3450L);
        expiry.setBalanceAfter(0L);
        expiry.setBizNo("membership-expire:77");
        transactionMapper.insert(expiry);

        BeanRenewalRestoreResult first = service.restoreExpiredForRenewal(
                USER_ID, "membership-expire:77", "renew-order-1", "连续续费恢复");
        BeanRenewalRestoreResult replay = service.restoreExpiredForRenewal(
                USER_ID, "membership-expire:77", "renew-order-1", "重复回调");

        assertEquals(3450L, first.restored());
        assertEquals(3450L, first.balanceAfter());
        assertEquals(3450L, accountMapper.balanceOf(USER_ID));
        assertEquals(0L, accountMapper.expiredOf(USER_ID));
        assertTrue(replay.alreadyProcessed());
        assertEquals("renew-order-1", replay.restoredByOrderNo());
        assertEquals(2, transactionMapper.records.size(), "只应有 EXPIRE 和首次 RENEW_RESTORE");
        BeanTransaction restore = transactionMapper.records.get(1);
        assertEquals("RENEW_RESTORE", restore.getType());
        assertEquals("renew-order-1", restore.getBizNo());
        assertEquals("membership-expire:77", restore.getSourceBizNo());
        assertEquals(3450L, restore.getChangeValue());
    }

    @Test
    void 同一笔到期清零不会被不同续费单重复恢复() {
        accountMapper.seed(USER_ID, 0L);
        accountMapper.setExpired(USER_ID, 1000L);
        BeanTransaction expiry = new BeanTransaction();
        expiry.setUserId(USER_ID);
        expiry.setType("EXPIRE");
        expiry.setChangeValue(-1000L);
        expiry.setBalanceAfter(0L);
        expiry.setBizNo("membership-expire:88");
        transactionMapper.insert(expiry);

        service.restoreExpiredForRenewal(
                USER_ID, "membership-expire:88", "renew-order-a", "首笔续费");
        BeanRenewalRestoreResult second = service.restoreExpiredForRenewal(
                USER_ID, "membership-expire:88", "renew-order-b", "第二笔续费");

        assertTrue(second.alreadyProcessed());
        assertEquals("renew-order-a", second.restoredByOrderNo());
        assertEquals(1000L, accountMapper.balanceOf(USER_ID));
        assertEquals(2, transactionMapper.records.size());
    }

    @Test
    void 到期后连续续费恢复原豆子批次_退旧会员单仍能精确回收() {
        accountMapper.seed(USER_ID, 0L);
        service.issue(USER_ID, 2000L, "old-member-order", "旧会员赠豆");
        service.recharge(USER_ID, 1000L, "old-addon-order", "旧加油包");
        service.deduct(USER_ID, 500L, "task-before-expiry", "生成扣豆");
        service.clearForMembershipExpiry(
                USER_ID, "membership-expire:501", "会员到期清零");

        BeanRenewalRestoreResult restored = service.restoreExpiredForRenewal(
                USER_ID, "membership-expire:501", "renew-order-501", "到期前已续费");
        BeanRefundRevokeResult revoked = service.revokeForOrderRefund(
                USER_ID, 2000L, "old-member-order", "旧会员单退款");

        assertEquals(2500L, restored.restored());
        assertEquals(1500L, revoked.revoked(), "应找回原会员批次已恢复的1500豆");
        assertEquals(1000L, accountMapper.balanceOf(USER_ID), "旧加油包剩余批次不得误扣");
    }

    @Test
    void 连续续费回调最晚到_同时恢复旧任务退豆并保留原批次() {
        accountMapper.seed(USER_ID, 0L);
        service.issue(USER_ID, 2000L, "member-before-delay", "会员赠豆");
        service.deduct(USER_ID, 500L, "late-failed-task", "生成扣豆");
        service.clearForMembershipExpiry(
                USER_ID, "membership-expire:502", "回调到达前已到期");
        service.refund(USER_ID, 500L, "late-failed-task", "旧任务失败晚到退豆");
        service.expireAmount(USER_ID, 500L, "post-expiry-refund:late-failed-task",
                "membership-expire:502|late-failed-task", "续费回调未到时临时清零");

        BeanRenewalRestoreResult restored = service.restoreExpiredForRenewal(
                USER_ID, "membership-expire:502", "renew-order-502", "到期前支付的续费回调到达");
        BeanRefundRevokeResult revoked = service.revokeForOrderRefund(
                USER_ID, 2000L, "member-before-delay", "原会员单退款");

        assertEquals(2000L, restored.restored(), "原清零1500+旧任务退豆500都应恢复");
        assertEquals(2000L, revoked.revoked(), "两次 EXPIRE 的豆都应回到原会员批次");
        assertEquals(0L, accountMapper.balanceOf(USER_ID));
        BeanTransaction restore = transactionMapper.records.stream()
                .filter(tx -> "RENEW_RESTORE".equals(tx.getType()))
                .findFirst()
                .orElseThrow();
        assertEquals("membership-expire:502", restore.getSourceBizNo());
    }

    @Test
    void 会员退款REVOKE_ALL后旧任务才失败_退豆不得混入后来新会员() {
        accountMapper.seed(USER_ID, 0L);
        service.issue(USER_ID, 2000L, "cancelled-member-order", "已退款会员赠豆");
        service.deduct(USER_ID, 500L, "task-before-cancel-all", "生成扣豆");
        service.revokeAllForOrderRefund(
                USER_ID, "cancelled-member-order", "会员退款后已无其他会员");
        service.issue(USER_ID, 2000L, "new-member-after-cancel", "后来新订会员");
        service.refund(USER_ID, 500L, "task-before-cancel-all", "旧任务失败退豆");

        long revoked = service.revokeCancelledTaskRefund(
                USER_ID, 500L, "task-before-cancel-all",
                "post-expiry-refund:task-before-cancel-all", "已取消权益退豆回收");

        assertEquals(500L, revoked);
        assertEquals(2000L, accountMapper.balanceOf(USER_ID), "后来新会员的2000豆必须完整保留");
        assertEquals(2000L, accountMapper.revokedOf(USER_ID), "原剩余1500+晚到退豆500均属退款回收");
        BeanTransaction lateRevoke = transactionMapper.records.get(transactionMapper.records.size() - 1);
        assertEquals("REVOKE", lateRevoke.getType());
        assertEquals("LATE_TASK:task-before-cancel-all", lateRevoke.getSourceBizNo());
    }

    @Test
    void 旧会员豆已全消耗_REVOKE_ALL零流水仍能阻止晚到退豆复活() {
        accountMapper.seed(USER_ID, 0L);
        service.issue(USER_ID, 2000L, "zero-revoke-member", "已退款会员赠豆");
        service.deduct(USER_ID, 2000L, "fully-used-task", "全部消耗");
        BeanRefundRevokeResult original = service.revokeAllForOrderRefund(
                USER_ID, "zero-revoke-member", "会员退款时余额已为零");
        service.issue(USER_ID, 2000L, "new-member-after-zero-revoke", "后来新订会员");
        service.refund(USER_ID, 500L, "fully-used-task", "旧任务部分失败退豆");

        long late = service.revokeCancelledTaskRefund(
                USER_ID, 500L, "fully-used-task",
                "post-expiry-refund:fully-used-task", "零余额退款后的晚到退豆回收");

        assertEquals(0L, original.revoked());
        assertEquals(500L, late, "变动值为0的 REVOKE_ALL 仍是权益取消边界");
        assertEquals(2000L, accountMapper.balanceOf(USER_ID));
        assertEquals(500L, accountMapper.revokedOf(USER_ID));
    }

    @Test
    void 会员退款TARGET回收后旧任务才失败_只撤回原订单批次() {
        accountMapper.seed(USER_ID, 0L);
        service.issue(USER_ID, 2000L, "cancelled-target-order", "待退款会员赠豆");
        service.deduct(USER_ID, 500L, "task-before-target-revoke", "生成扣豆");
        service.issue(USER_ID, 2000L, "another-valid-member", "其他有效会员赠豆");
        BeanRefundRevokeResult original = service.revokeForOrderRefund(
                USER_ID, 2000L, "cancelled-target-order", "只退目标会员单");
        service.refund(USER_ID, 500L, "task-before-target-revoke", "旧任务失败退豆");

        long late = service.revokeCancelledTaskRefund(
                USER_ID, 500L, "task-before-target-revoke",
                "post-expiry-refund:task-before-target-revoke", "已取消目标批次退豆回收");

        assertEquals(1500L, original.revoked());
        assertEquals(500L, late);
        assertEquals(2000L, accountMapper.balanceOf(USER_ID), "其他有效会员批次不得被误扣");
        assertEquals(2000L, accountMapper.revokedOf(USER_ID));
    }

    @Test
    void TARGET批次已全耗尽_零额REVOKE仍阻止旧任务退豆混入其他会员() {
        accountMapper.seed(USER_ID, 0L);
        service.issue(USER_ID, 2000L, "fully-used-target-order", "待退款会员赠豆");
        service.deduct(USER_ID, 2000L, "fully-used-target-task", "目标批次全部消耗");
        service.issue(USER_ID, 2000L, "other-member-after-target-use", "其他有效会员赠豆");
        BeanRefundRevokeResult original = service.revokeForOrderRefund(
                USER_ID, 2000L, "fully-used-target-order", "退款时目标批次已耗尽");
        service.refund(USER_ID, 500L, "fully-used-target-task", "旧任务部分失败退豆");

        long late = service.revokeCancelledTaskRefund(
                USER_ID, 500L, "fully-used-target-task",
                "post-expiry-refund:fully-used-target-task", "零额TARGET后晚到退豆回收");

        assertEquals(0L, original.revoked());
        assertEquals(500L, late, "零变动 TARGET REVOKE 仍必须标记原批次已取消");
        assertEquals(2000L, accountMapper.balanceOf(USER_ID), "其他会员批次必须完整保留");
        assertEquals(500L, accountMapper.revokedOf(USER_ID));
    }

    @Test
    void 并发扣减不超扣且流水与余额一致() throws InterruptedException {
        long initial = 1000L;
        long perDeduct = 7L;
        int threads = 32;
        int attemptsPerThread = 50;
        accountMapper.seed(USER_ID, initial);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < attemptsPerThread; i++) {
                        try {
                            service.deduct(USER_ID, perDeduct, null, "并发扣费");
                            successCount.incrementAndGet();
                        } catch (ServiceException ignored) {
                            // 余额不足属预期,计入失败不增加成功数
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "并发任务应在超时内完成");
        pool.shutdownNow();

        long finalBalance = accountMapper.balanceOf(USER_ID);
        int success = successCount.get();

        // 1. 终余额不为负(核心:不超扣)
        assertTrue(finalBalance >= 0, "终余额不应为负: " + finalBalance);
        // 2. 终余额 = 初始 - 成功次数*单笔
        assertEquals(initial - (long) success * perDeduct, finalBalance, "余额与成功扣减次数不一致");
        // 3. 余额无法再扣一笔(已扣到极限),即剩余 < 单笔
        assertTrue(finalBalance < perDeduct, "应已扣到无法再扣: " + finalBalance);
        // 4. 流水条数 = 成功扣减次数,且每条余额快照非负
        assertEquals(success, transactionMapper.records.size(), "流水条数应等于成功扣减次数");
        transactionMapper.records.forEach(tx ->
                assertTrue(tx.getBalanceAfter() >= 0, "流水余额快照不应为负"));
        // 5. 累计消耗 = 成功次数*单笔
        assertEquals((long) success * perDeduct, accountMapper.consumedOf(USER_ID), "累计消耗与成功次数不一致");
    }

    /**
     * 反射注入 ServiceImpl.baseMapper(由 Spring 注入,单测手动注入内存版)。
     */
    private static void injectBaseMapper(BeanAccountServiceImpl target, BeanAccountMapper mapper) throws Exception {
        Field field = findField(target.getClass(), "baseMapper");
        field.setAccessible(true);
        field.set(target, mapper);
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> c = clazz;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
