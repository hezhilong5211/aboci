package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.MembershipLevel;
import com.aurora.admin.mobile.domain.MobileUser;
import com.aurora.admin.mobile.domain.UserMembership;
import com.aurora.admin.mobile.mapper.MembershipLevelMapper;
import com.aurora.admin.mobile.mapper.UserMembershipMapper;
import com.aurora.admin.mobile.service.impl.UserMembershipServiceImpl;
import com.aurora.admin.mobile.vo.MembershipInfoVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserMembershipService 单测:验证会员管理三条核心红线(无数据库/无 Spring 容器,mock 协作者)。
 *   ① 快照兜底:开通后改/删 membership_level,user_membership 快照与「当前等级」不变。
 *   ② 叠加:同用户两段有效记录,当前等级取 level_sort 最高那条。
 *   ③ 过期段不计入当前等级。
 */
class UserMembershipServiceTest {

    private static final long USER_ID = 1001L;

    private UserMembershipServiceImpl service;
    private UserMembershipMapper userMembershipMapper;
    private MembershipLevelMapper membershipLevelMapper;
    private IBeanAccountService beanAccountService;
    private IMobileUserService mobileUserService;
    private IMembershipExpiryService membershipExpiryService;

    @BeforeEach
    void setUp() throws Exception {
        userMembershipMapper = mock(UserMembershipMapper.class);
        membershipLevelMapper = mock(MembershipLevelMapper.class);
        beanAccountService = mock(IBeanAccountService.class);
        mobileUserService = mock(IMobileUserService.class);
        membershipExpiryService = mock(IMembershipExpiryService.class);
        service = new UserMembershipServiceImpl(
                membershipLevelMapper, beanAccountService, mobileUserService,
                membershipExpiryService);
        // ServiceImpl 的 baseMapper 由 Spring 注入,单测用反射注入 mock
        injectBaseMapper(service, userMembershipMapper);
    }

    @Test
    void 开通写入快照_后续改删等级配置快照与当前等级不变() {
        // 开通时的等级配置
        MembershipLevel level = level(10L, "黄金会员", 100L, new BigDecimal("19.90"), 5, "0");
        level.setDurationDays(90);
        when(mobileUserService.getById(USER_ID)).thenReturn(new MobileUser());
        when(membershipLevelMapper.selectById(10L)).thenReturn(level);
        // 捕获 save(insert) 写入的会员段
        ArgumentCaptor<UserMembership> captor = ArgumentCaptor.forClass(UserMembership.class);
        when(userMembershipMapper.insert(captor.capture())).thenAnswer(inv -> {
            inv.getArgument(0, UserMembership.class).setId(900L);
            return 1;
        });

        service.grant(USER_ID, 10L, 365);

        UserMembership saved = captor.getValue();
        // 快照写入 = 开通时等级值
        assertEquals("黄金会员", saved.getLevelName());
        assertEquals(5, saved.getLevelSort());
        assertEquals(100L, saved.getSnapshotGrantBeans());
        assertEquals(0, new BigDecimal("19.90").compareTo(saved.getSnapshotPrice()));
        assertEquals("MANUAL", saved.getSource());
        assertNull(saved.getPaidTime(), "后台手工开通没有真实支付时间");
        assertEquals("0", saved.getStatus());
        assertEquals(90L,
                java.time.Duration.between(saved.getStartTime(), saved.getExpireTime()).toDays(),
                "手动开通必须以所选等级当前配置为准");
        // 发订阅豆按快照送豆数,bizNo = 段 id
        verify(beanAccountService).issue(eq(USER_ID), eq(100L), eq("900"), anyString());

        // 模拟后续修改等级配置(改名/改送豆数)甚至删除——已写入的段为独立快照对象,不应被影响
        level.setName("白金会员");
        level.setGrantBeans(999L);
        level.setSort(99);
        // 当前等级读的是 user_membership 快照,与现 level 无关
        UserMembership current = service.resolveCurrentSegment(List.of(saved), MembershipPolicy.now());
        assertNotNull(current);
        assertEquals("黄金会员", current.getLevelName(), "快照等级名不随配置变更");
        assertEquals(5, current.getLevelSort(), "快照等级序不随配置变更");
        assertEquals(100L, current.getSnapshotGrantBeans(), "快照送豆数不随配置变更");
    }

    @Test
    void 叠加_同用户两段有效记录当前等级取level_sort最高() {
        LocalDateTime now = MembershipPolicy.now();
        UserMembership low = segment(1L, 3, "白银会员", now.plusDays(10));   // sort 低
        UserMembership high = segment(2L, 7, "黄金会员", now.plusDays(5));   // sort 高(到期更早,但等级更高)

        UserMembership current = service.resolveCurrentSegment(List.of(low, high), now);

        assertNotNull(current);
        assertEquals("黄金会员", current.getLevelName(), "当前等级应取 level_sort 最高那条");
        assertEquals(7, current.getLevelSort());
    }

    @Test
    void 过期段不计入当前等级() {
        LocalDateTime now = MembershipPolicy.now();
        // 高等级段已过期(expire<now),低等级段仍有效 → 当前等级应为低等级段
        UserMembership expiredHigh = segment(1L, 9, "钻石会员", now.minusDays(1));
        UserMembership validLow = segment(2L, 2, "白银会员", now.plusDays(10));

        UserMembership current = service.resolveCurrentSegment(List.of(expiredHigh, validLow), now);

        assertNotNull(current);
        assertEquals("白银会员", current.getLevelName(), "过期高等级段不应计入,当前等级取有效低等级段");
        assertEquals(2, current.getLevelSort());
    }

    @Test
    void 全部段过期或取消时无当前等级() {
        LocalDateTime now = MembershipPolicy.now();
        UserMembership expired = segment(1L, 5, "黄金会员", now.minusDays(1));   // 过期
        UserMembership cancelled = segment(2L, 8, "钻石会员", now.plusDays(10));
        cancelled.setStatus("2"); // 取消

        UserMembership current = service.resolveCurrentSegment(List.of(expired, cancelled), now);

        assertNull(current, "无有效段时当前等级为 null(非会员)");
    }

    @Test
    void 已付费但尚未到startTime的续期段不提前算当前会员() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 10, 12, 0);
        UserMembership future = segment(3L, 9, "钻石会员",
                now.plusDays(MembershipPolicy.DURATION_DAYS * 2L));
        future.setStartTime(now.plusDays(MembershipPolicy.DURATION_DAYS));

        assertNull(service.resolveCurrentSegment(List.of(future), now));
    }

    @Test
    void 当前会员接口展示连续续费链最终到期日() {
        LocalDateTime now = MembershipPolicy.now();
        UserMembership current = segment(1L, 1, "青铜会员", now.plusDays(5));
        current.setStartTime(now.minusDays(25));
        UserMembership renewal1 = segment(2L, 1, "青铜会员", now.plusDays(35));
        renewal1.setStartTime(current.getExpireTime());
        UserMembership renewal2 = segment(3L, 1, "青铜会员", now.plusDays(65));
        renewal2.setStartTime(renewal1.getExpireTime());
        when(userMembershipMapper.selectList(any())).thenReturn(List.of(current, renewal1, renewal2));

        MembershipInfoVO info = service.getCurrentMembership(USER_ID);

        assertEquals(renewal2.getExpireTime(), info.getExpireTime());
        assertEquals(65, info.getRemainDays());
    }

    @Test
    void 到期前支付_新段从已购会员链末续期() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        LocalDateTime grantAt = paidAt.plusMinutes(3);
        LocalDateTime firstExpire = paidAt.plusHours(1);
        UserMembership current = segment(1L, 1, "青铜会员", firstExpire);
        current.setStartTime(firstExpire.minusDays(MembershipPolicy.DURATION_DAYS));
        UserMembership purchasedRenewal = segment(2L, 1, "青铜会员",
                firstExpire.plusDays(MembershipPolicy.DURATION_DAYS));
        purchasedRenewal.setStartTime(firstExpire);
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(current, purchasedRenewal));

        UserMembership saved = grantPaymentAndCapture("pay-renew-chain", paidAt, grantAt);

        assertEquals(purchasedRenewal.getExpireTime(), saved.getStartTime());
        assertEquals(purchasedRenewal.getExpireTime().plusDays(MembershipPolicy.DURATION_DAYS),
                saved.getExpireTime());
        verify(membershipExpiryService).settleUserIfExpired(USER_ID, grantAt);
    }

    @Test
    void 到期前一秒支付_从旧到期时刻续期() {
        LocalDateTime oldExpire = LocalDateTime.of(2026, 7, 10, 12, 0);
        LocalDateTime paidAt = oldExpire.minusSeconds(1);
        UserMembership current = segment(1L, 1, "青铜会员", oldExpire);
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID)).thenReturn(List.of(current));

        UserMembership saved = grantPaymentAndCapture("pay-before-expiry-one-second", paidAt, paidAt);

        assertEquals(oldExpire, saved.getStartTime());
        assertEquals(oldExpire.plusDays(MembershipPolicy.DURATION_DAYS), saved.getExpireTime());
    }

    @Test
    void 支付订单快照为90天_会员段按90天开通() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID)).thenReturn(List.of());

        UserMembership saved = grantPaymentAndCapture(
                "pay-duration-90", paidAt, paidAt, 90);

        assertEquals(paidAt, saved.getStartTime());
        assertEquals(paidAt.plusDays(90), saved.getExpireTime());
    }

    @Test
    void 恰好到期时支付_按新订从paidAt开始且不恢复旧周期豆子() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        UserMembership expiredAtBoundary = segment(1L, 1, "青铜会员", paidAt);
        expiredAtBoundary.setExpirySettledTime(paidAt);
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(expiredAtBoundary));

        UserMembership saved = grantPaymentAndCapture("pay-at-expiry", paidAt, paidAt);

        assertEquals(paidAt, saved.getStartTime());
        assertEquals(paidAt.plusDays(MembershipPolicy.DURATION_DAYS), saved.getExpireTime());
        verify(beanAccountService, never()).restoreExpiredForRenewal(
                eq(USER_ID), anyString(), eq("pay-at-expiry"), anyString());
    }

    @Test
    void 到期后一秒支付_按新订从paidAt开始() {
        LocalDateTime oldExpire = LocalDateTime.of(2026, 7, 10, 12, 0);
        LocalDateTime paidAt = oldExpire.plusSeconds(1);
        UserMembership expired = segment(1L, 1, "青铜会员", oldExpire);
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID)).thenReturn(List.of(expired));

        UserMembership saved = grantPaymentAndCapture("pay-after-expiry-one-second", paidAt, paidAt);

        assertEquals(paidAt, saved.getStartTime());
        assertEquals(paidAt.plusDays(MembershipPolicy.DURATION_DAYS), saved.getExpireTime());
    }

    @Test
    void 支付时已过期_即使回调立刻到达也从paidAt新订365天() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        LocalDateTime grantAt = paidAt.plusNanos(1);
        UserMembership expired = segment(1L, 1, "青铜会员", paidAt.minusNanos(1));
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID)).thenReturn(List.of(expired));

        UserMembership saved = grantPaymentAndCapture("pay-new-after-expiry", paidAt, grantAt);

        assertEquals(paidAt, saved.getStartTime());
        assertEquals(paidAt.plusDays(MembershipPolicy.DURATION_DAYS), saved.getExpireTime());
    }

    @Test
    void 支付在到期前但回调在到期后_仍按paidAt无缝续期() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 7, 10, 11, 59, 59);
        LocalDateTime oldExpire = paidAt.plusSeconds(1);
        LocalDateTime grantAt = oldExpire.plusSeconds(20);
        UserMembership expiredByGrantTime = segment(1L, 1, "青铜会员", oldExpire);
        expiredByGrantTime.setStatus("1");
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(expiredByGrantTime));

        UserMembership saved = grantPaymentAndCapture("pay-delayed-callback", paidAt, grantAt);

        assertEquals(oldExpire, saved.getStartTime(), "回调延迟不应把履约时间当成新订起点");
        assertEquals(oldExpire.plusDays(MembershipPolicy.DURATION_DAYS), saved.getExpireTime());
        InOrder order = inOrder(beanAccountService, userMembershipMapper, membershipExpiryService);
        order.verify(beanAccountService).lockAccount(USER_ID);
        order.verify(userMembershipMapper).selectByUserIdForUpdate(USER_ID);
        order.verify(membershipExpiryService).settleUserIfExpired(USER_ID, paidAt);
        order.verify(userMembershipMapper).insert(any(UserMembership.class));
        order.verify(beanAccountService).issue(eq(USER_ID), eq(2000L),
                eq("pay-delayed-callback"), anyString());
        order.verify(membershipExpiryService).settleUserIfExpired(USER_ID, grantAt);
    }

    @Test
    void 到期前续费回调晚于到期后新订_后订段顺延避免两笔会员重叠() {
        LocalDateTime oldExpire = LocalDateTime.of(2026, 7, 10, 12, 0);
        LocalDateTime paidAt = oldExpire.minusSeconds(1);
        LocalDateTime grantAt = oldExpire.plusSeconds(2);
        UserMembership old = segment(1L, 1, "青铜会员", oldExpire);
        old.setStatus("1");
        UserMembership laterNewSubscription = segment(
                2L, 1, "青铜会员",
                oldExpire.plusSeconds(1).plusDays(MembershipPolicy.DURATION_DAYS));
        laterNewSubscription.setStartTime(oldExpire.plusSeconds(1));
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(old, laterNewSubscription));
        when(userMembershipMapper.rescheduleValidSegment(
                eq(2L), eq(USER_ID), eq(oldExpire.plusDays(MembershipPolicy.DURATION_DAYS)),
                eq(oldExpire.plusDays(MembershipPolicy.DURATION_DAYS * 2L)), eq(grantAt))).thenReturn(1);

        UserMembership saved = grantPaymentAndCapture("delayed-renew-before-new-sub", paidAt, grantAt);

        assertEquals(oldExpire, saved.getStartTime());
        assertEquals(oldExpire.plusDays(MembershipPolicy.DURATION_DAYS), saved.getExpireTime());
        assertEquals(oldExpire.plusDays(MembershipPolicy.DURATION_DAYS), laterNewSubscription.getStartTime());
        assertEquals(oldExpire.plusDays(MembershipPolicy.DURATION_DAYS * 2L),
                laterNewSubscription.getExpireTime());
    }

    @Test
    void 迟到30天续费使后购90天会员顺延_两段各自时长保持不变() {
        LocalDateTime oldExpire = LocalDateTime.of(2026, 7, 10, 12, 0);
        LocalDateTime paidAt = oldExpire.minusSeconds(1);
        LocalDateTime grantAt = oldExpire.plusSeconds(2);
        UserMembership old = segment(1L, 1, "旧会员", oldExpire);
        old.setStatus("1");
        UserMembership laterNinetyDays = segment(
                2L, 1, "90天会员", oldExpire.plusSeconds(1).plusDays(90));
        laterNinetyDays.setStartTime(oldExpire.plusSeconds(1));
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(old, laterNinetyDays));
        when(userMembershipMapper.rescheduleValidSegment(
                eq(2L), eq(USER_ID), eq(oldExpire.plusDays(30)),
                eq(oldExpire.plusDays(120)), eq(grantAt))).thenReturn(1);

        UserMembership saved = grantPaymentAndCapture(
                "pay-delayed-duration-30", paidAt, grantAt, 30);

        assertEquals(oldExpire, saved.getStartTime());
        assertEquals(oldExpire.plusDays(30), saved.getExpireTime());
        assertEquals(oldExpire.plusDays(30), laterNinetyDays.getStartTime());
        assertEquals(oldExpire.plusDays(120), laterNinetyDays.getExpireTime());
    }

    @Test
    void 到期前付款A晚回调_恰好到期付款B先回调_A恢复旧豆且B顺延() {
        LocalDateTime oldExpire = LocalDateTime.of(2026, 7, 10, 12, 0);
        LocalDateTime paidAtA = oldExpire.minusSeconds(1);
        LocalDateTime paidAtB = oldExpire;
        LocalDateTime grantAtA = oldExpire.plusSeconds(2);

        UserMembership old = segment(1L, 1, "青铜会员", oldExpire);
        old.setStatus("1");
        old.setExpirySettledTime(oldExpire);

        UserMembership boundaryNewSubscription = segment(
                2L, 1, "青铜会员", paidAtB.plusDays(MembershipPolicy.DURATION_DAYS));
        boundaryNewSubscription.setStartTime(paidAtB);
        boundaryNewSubscription.setSource("PAY");
        boundaryNewSubscription.setSourceOrderNo("pay-b-at-expiry");
        boundaryNewSubscription.setPaidTime(paidAtB);

        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(old, boundaryNewSubscription));
        when(userMembershipMapper.rescheduleValidSegment(
                eq(2L), eq(USER_ID), eq(oldExpire.plusDays(MembershipPolicy.DURATION_DAYS)),
                eq(oldExpire.plusDays(MembershipPolicy.DURATION_DAYS * 2L)), eq(grantAtA))).thenReturn(1);

        UserMembership savedA = grantPaymentAndCapture("pay-a-before-expiry", paidAtA, grantAtA);

        assertEquals(oldExpire, savedA.getStartTime(),
                "A 按真实付款时刻应紧接旧会员，不应被后付款但先回调的 B 挤到后面");
        assertEquals(paidAtA, savedA.getPaidTime(), "会员段必须固化不可变真实支付时间");
        assertEquals(oldExpire.plusDays(MembershipPolicy.DURATION_DAYS), savedA.getExpireTime());
        assertEquals(oldExpire.plusDays(MembershipPolicy.DURATION_DAYS), boundaryNewSubscription.getStartTime());
        assertEquals(oldExpire.plusDays(MembershipPolicy.DURATION_DAYS * 2L),
                boundaryNewSubscription.getExpireTime());
        verify(beanAccountService).restoreExpiredForRenewal(USER_ID,
                "membership-expire:1", "pay-a-before-expiry",
                "续费支付早于旧会员到期，恢复回调延迟期间被清零的豆子");
    }

    @Test
    void 到期任务已先清零_延迟回调按原expire流水恢复且再续期() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 7, 10, 11, 59, 59);
        LocalDateTime oldExpire = paidAt.plusSeconds(1);
        LocalDateTime grantAt = oldExpire.plusMinutes(1);
        UserMembership alreadySettled = segment(77L, 1, "青铜会员", oldExpire);
        alreadySettled.setStatus("1");
        alreadySettled.setExpirySettledTime(oldExpire);
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(alreadySettled));

        UserMembership saved = grantPaymentAndCapture("pay-restore-expired", paidAt, grantAt);

        assertEquals(oldExpire, saved.getStartTime());
        verify(beanAccountService).restoreExpiredForRenewal(USER_ID,
                "membership-expire:77", "pay-restore-expired",
                "续费支付早于旧会员到期，恢复回调延迟期间被清零的豆子");
    }

    @Test
    void 取消段与有缺口的未来段不延长当前续期链() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        LocalDateTime currentExpire = paidAt.plusDays(2);
        UserMembership current = segment(1L, 1, "青铜会员", currentExpire);
        UserMembership cancelled = segment(2L, 1, "青铜会员",
                currentExpire.plusDays(MembershipPolicy.DURATION_DAYS));
        cancelled.setStartTime(currentExpire);
        cancelled.setStatus("2");
        UserMembership afterGap = segment(3L, 1, "青铜会员",
                currentExpire.plusDays(MembershipPolicy.DURATION_DAYS * 2L + 1L));
        afterGap.setStartTime(currentExpire.plusDays(MembershipPolicy.DURATION_DAYS + 1L));
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(current, cancelled, afterGap));

        UserMembership saved = grantPaymentAndCapture("pay-ignore-cancel-gap", paidAt, paidAt);

        assertEquals(currentExpire, saved.getStartTime());
        assertEquals(currentExpire.plusDays(MembershipPolicy.DURATION_DAYS), saved.getExpireTime());
    }

    @Test
    void 非有效会员购买加油包被拒绝() {
        when(userMembershipMapper.selectCount(any())).thenReturn(0L);

        com.aurora.common.core.exception.ServiceException ex = assertThrows(
                com.aurora.common.core.exception.ServiceException.class,
                () -> service.requireActiveMembership(USER_ID));

        assertEquals("加油包仅限有效会员购买", ex.getMessage());
        verify(membershipExpiryService).settleUserIfExpired(USER_ID);
    }

    @Test
    void 后台调整当前等级_只改生效段且不发豆不改期限() {
        LocalDateTime now = MembershipPolicy.now();
        UserMembership active1 = segment(1L, 1, "青铜会员", now.plusDays(20));
        UserMembership active2 = segment(2L, 2, "白银会员", now.plusDays(30));
        MembershipLevel target = level(9L, "黄金会员", 999L,
                new BigDecimal("299.00"), 9, "0");
        when(mobileUserService.getById(USER_ID)).thenReturn(new MobileUser());
        when(membershipLevelMapper.selectById(9L)).thenReturn(target);
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(active1, active2));
        when(userMembershipMapper.adjustActiveLevel(any(), eq(USER_ID), eq(9L), eq(9),
                eq("黄金会员"), any(LocalDateTime.class))).thenReturn(1);

        service.adjustCurrentLevel(USER_ID, 9L);

        verify(beanAccountService).lockAccount(USER_ID);
        verify(userMembershipMapper, times(2)).adjustActiveLevel(any(), eq(USER_ID), eq(9L), eq(9),
                eq("黄金会员"), any(LocalDateTime.class));
        verify(beanAccountService, never()).issue(any(), anyLong(), anyString(), anyString());
        verify(userMembershipMapper, never()).insert(any(UserMembership.class));
        assertEquals(now.plusDays(20), active1.getExpireTime());
        assertEquals(now.plusDays(30), active2.getExpireTime());
    }

    @Test
    void 后台选择无_只取消当前生效段() {
        when(mobileUserService.getById(USER_ID)).thenReturn(new MobileUser());
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID)).thenReturn(List.of());

        service.adjustCurrentLevel(USER_ID, null);

        verify(beanAccountService).lockAccount(USER_ID);
        verify(userMembershipMapper).cancelActiveForAdmin(eq(USER_ID), any(LocalDateTime.class));
        verify(beanAccountService, never()).issue(any(), anyLong(), anyString(), anyString());
    }

    @Test
    void 无当前会员时后台选择等级_按等级配置新增零赠豆段() {
        MembershipLevel target = level(9L, "黄金会员", 999L,
                new BigDecimal("299.00"), 9, "0");
        target.setDurationDays(180);
        when(mobileUserService.getById(USER_ID)).thenReturn(new MobileUser());
        when(membershipLevelMapper.selectById(9L)).thenReturn(target);
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID)).thenReturn(List.of());
        ArgumentCaptor<UserMembership> captor = ArgumentCaptor.forClass(UserMembership.class);
        when(userMembershipMapper.insert(captor.capture())).thenReturn(1);

        service.adjustCurrentLevel(USER_ID, 9L);

        UserMembership saved = captor.getValue();
        assertEquals(0L, saved.getSnapshotGrantBeans());
        assertEquals("黄金会员", saved.getLevelName());
        assertEquals(180L,
                java.time.Duration.between(saved.getStartTime(), saved.getExpireTime()).toDays());
        verify(beanAccountService, never()).issue(any(), anyLong(), anyString(), anyString());
    }

    // ===== 构造辅助 =====

    private MembershipLevel level(long levelId, String name, long grantBeans, BigDecimal price, int sort, String status) {
        MembershipLevel l = new MembershipLevel();
        l.setLevelId(levelId);
        l.setName(name);
        l.setGrantBeans(grantBeans);
        l.setPrice(price);
        l.setDurationDays(MembershipPolicy.DURATION_DAYS);
        l.setSort(sort);
        l.setStatus(status);
        return l;
    }

    /** 构造一条有效(status=0)会员段 */
    private UserMembership segment(long id, int levelSort, String levelName, LocalDateTime expireTime) {
        UserMembership s = new UserMembership();
        s.setId(id);
        s.setUserId(USER_ID);
        s.setLevelId(id);
        s.setLevelSort(levelSort);
        s.setLevelName(levelName);
        s.setStatus("0");
        s.setStartTime(expireTime.minusDays(MembershipPolicy.DURATION_DAYS));
        s.setExpireTime(expireTime);
        return s;
    }

    private UserMembership grantPaymentAndCapture(String orderNo, LocalDateTime paidAt,
                                                   LocalDateTime grantAt) {
        return grantPaymentAndCapture(
                orderNo, paidAt, grantAt, MembershipPolicy.DURATION_DAYS);
    }

    private UserMembership grantPaymentAndCapture(String orderNo, LocalDateTime paidAt,
                                                   LocalDateTime grantAt, int durationDays) {
        when(mobileUserService.getById(USER_ID)).thenReturn(new MobileUser());
        ArgumentCaptor<UserMembership> captor = ArgumentCaptor.forClass(UserMembership.class);
        when(userMembershipMapper.insert(captor.capture())).thenAnswer(invocation -> {
            invocation.getArgument(0, UserMembership.class).setId(999L);
            return 1;
        });
        service.grantFromPayment(USER_ID, 10L, 1, "青铜会员", 2000L,
                new BigDecimal("1.99"), durationDays,
                orderNo, paidAt, grantAt);
        UserMembership saved = captor.getValue();
        assertEquals(paidAt, saved.getPaidTime(), "PAY 会员段必须在插入时持久化真实支付时间");
        return saved;
    }

    /**
     * 反射注入 ServiceImpl.baseMapper(由 Spring 注入,单测手动注入 mock)。
     */
    private static void injectBaseMapper(UserMembershipServiceImpl target, UserMembershipMapper mapper) throws Exception {
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
