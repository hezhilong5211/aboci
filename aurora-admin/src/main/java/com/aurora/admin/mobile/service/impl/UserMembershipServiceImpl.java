package com.aurora.admin.mobile.service.impl;

import com.aurora.admin.mobile.domain.MembershipLevel;
import com.aurora.admin.mobile.domain.MobileUser;
import com.aurora.admin.mobile.domain.UserMembership;
import com.aurora.admin.mobile.exception.MembershipRequiredException;
import com.aurora.admin.mobile.mapper.MembershipLevelMapper;
import com.aurora.admin.mobile.mapper.UserMembershipMapper;
import com.aurora.admin.mobile.service.IBeanAccountService;
import com.aurora.admin.mobile.service.IMembershipExpiryService;
import com.aurora.admin.mobile.service.IMobileUserService;
import com.aurora.admin.mobile.service.IUserMembershipService;
import com.aurora.admin.mobile.service.MembershipPolicy;
import com.aurora.admin.mobile.vo.MembershipInfoVO;
import com.aurora.admin.mobile.vo.MembershipUserVO;
import com.aurora.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户会员段 Service 实现(叠加制开通 + 当前等级判定 + 会员列表)
 * 红线:已开通权益快照——开通时把当时等级信息快照入 user_membership,
 * 此后删改 membership_level 不影响已开通用户;会员列表/当前等级只读快照。
 */
@Service
@RequiredArgsConstructor
public class UserMembershipServiceImpl extends ServiceImpl<UserMembershipMapper, UserMembership>
        implements IUserMembershipService {

    private final MembershipLevelMapper membershipLevelMapper;
    private final IBeanAccountService beanAccountService;
    private final IMobileUserService mobileUserService;
    private final IMembershipExpiryService membershipExpiryService;

    /** 会员段状态:有效 */
    private static final String STATUS_VALID = "0";
    /** 会员段状态:取消 */
    private static final String STATUS_CANCELLED = "2";
    /** 等级状态:停用 */
    private static final String LEVEL_STATUS_DISABLED = "1";
    /** 来源:后台手动开通 */
    private static final String SOURCE_MANUAL = "MANUAL";
    /** 来源:支付开通 */
    private static final String SOURCE_PAY = "PAY";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grant(Long userId, Long levelId, Integer durationDays) {
        if (userId == null || levelId == null) {
            throw new ServiceException("用户与等级不能为空");
        }
        // 校验用户存在
        MobileUser user = mobileUserService.getById(userId);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }
        // 查等级(@TableLogic 自动排除已删除);停用等级不可开通
        MembershipLevel level = membershipLevelMapper.selectById(levelId);
        if (level == null) {
            throw new ServiceException("会员等级不存在或已删除");
        }
        if (LEVEL_STATUS_DISABLED.equals(level.getStatus())) {
            throw new ServiceException("该会员等级已停用,不可开通");
        }

        LocalDateTime now = MembershipPolicy.now();
        createSegment(userId, level.getLevelId(), level.getSort(), level.getName(),
                level.getGrantBeans(), level.getPrice(), requireDurationDays(level.getDurationDays()),
                SOURCE_MANUAL, null, now, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantFromPayment(Long userId, Long levelId, Integer levelSort, String levelName,
                                 Long grantBeans, BigDecimal price, Integer durationDays,
                                 String sourceOrderNo) {
        LocalDateTime now = MembershipPolicy.now();
        grantFromPaymentInternal(userId, levelId, levelSort, levelName, grantBeans, price,
                durationDays, sourceOrderNo, now, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantFromPayment(Long userId, Long levelId, Integer levelSort, String levelName,
                                 Long grantBeans, BigDecimal price, Integer durationDays,
                                 String sourceOrderNo, LocalDateTime paidAt) {
        grantFromPaymentInternal(userId, levelId, levelSort, levelName, grantBeans, price,
                durationDays, sourceOrderNo, paidAt, MembershipPolicy.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantFromPayment(Long userId, Long levelId, Integer levelSort, String levelName,
                                 Long grantBeans, BigDecimal price, Integer durationDays,
                                 String sourceOrderNo, LocalDateTime paidAt, LocalDateTime grantAt) {
        grantFromPaymentInternal(userId, levelId, levelSort, levelName, grantBeans, price,
                durationDays, sourceOrderNo, paidAt, grantAt);
    }

    private void grantFromPaymentInternal(Long userId, Long levelId, Integer levelSort, String levelName,
                                          Long grantBeans, BigDecimal price, Integer durationDays,
                                          String sourceOrderNo,
                                          LocalDateTime paidAt, LocalDateTime grantAt) {
        if (userId == null || levelId == null || sourceOrderNo == null || sourceOrderNo.isBlank()) {
            throw new ServiceException("支付会员快照不完整");
        }
        if (levelSort == null || levelName == null || levelName.isBlank()
                || grantBeans == null || grantBeans < 0 || price == null) {
            throw new ServiceException("支付会员权益快照不完整");
        }
        int effectiveDurationDays = requireDurationDays(durationDays);
        MobileUser user = mobileUserService.getById(userId);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }
        if (paidAt == null || grantAt == null) {
            throw new ServiceException("支付时间与履约时间不能为空");
        }
        if (grantAt.isBefore(paidAt)) {
            throw new ServiceException("履约时间不能早于支付时间");
        }

        createSegment(userId, levelId, levelSort, levelName, grantBeans, price, effectiveDurationDays,
                SOURCE_PAY, sourceOrderNo, paidAt, grantAt);
    }

    /**
     * 创建会员段并发放赠豆。支付场景 bizNo 使用订单号，手工场景使用会员段 ID。
     */
    private void createSegment(Long userId, Long levelId, Integer levelSort, String levelName,
                               Long grantBeans, BigDecimal price, int durationDays,
                               String source, String sourceOrderNo,
                               LocalDateTime paidAt, LocalDateTime grantAt) {
        // 统一原子锁序：账户锁 → 会员段锁。定时到期结算也使用同一顺序。
        beanAccountService.lockAccount(userId);
        List<UserMembership> lockedSegments = baseMapper.selectByUserIdForUpdate(userId);
        if (sourceOrderNo != null && lockedSegments != null && lockedSegments.stream()
                .anyMatch(segment -> sourceOrderNo.equals(segment.getSourceOrderNo()))) {
            return;
        }
        LocalDateTime startTime = resolveRenewalStart(lockedSegments, paidAt);

        // 先按支付时刻结算历史：续期时旧段在 paidAt 仍有效，不会清零；
        // paidAt 时已过期则会先清掉旧周期豆子，再发放新订会员权益。
        membershipExpiryService.settleUserIfExpired(userId, paidAt);

        // 若支付确实发生在旧链到期前，但回调到达前定时任务已将旧豆清零，
        // 则按原 EXPIRE 流水恢复实际清零数。新订(startTime=paidAt)绝不执行恢复。
        UserMembership settledTerminal = findSettledRenewalTerminal(lockedSegments, paidAt, startTime);
        if (SOURCE_PAY.equals(source) && sourceOrderNo != null && settledTerminal != null) {
            beanAccountService.restoreExpiredForRenewal(userId,
                    "membership-expire:" + settledTerminal.getId(), sourceOrderNo,
                    "续费支付早于旧会员到期，恢复回调延迟期间被清零的豆子");
        }

        // 极端延迟场景：一笔到期前续费尚未回调，用户在到期后又买了新会员并先回调。
        // 新订段会从到期后一瞬开始，与迟到续费段重叠；将重叠的后续段整体顺延，
        // 每段都保留其购买时固化的实际时长。
        LocalDateTime expireTime = startTime.plusDays(durationDays);
        reflowOverlappingFutureSegments(userId, lockedSegments, startTime, expireTime, grantAt);
        UserMembership segment = new UserMembership();
        segment.setUserId(userId);
        segment.setLevelId(levelId);
        segment.setLevelSort(levelSort);
        segment.setLevelName(levelName);
        segment.setSnapshotGrantBeans(grantBeans == null ? 0L : grantBeans);
        segment.setSnapshotPrice(price);
        segment.setSource(source);
        segment.setSourceOrderNo(sourceOrderNo);
        segment.setPaidTime(SOURCE_PAY.equals(source) ? paidAt : null);
        segment.setStartTime(startTime);
        segment.setExpireTime(expireTime);
        segment.setStatus(STATUS_VALID);
        save(segment);

        long beans = segment.getSnapshotGrantBeans() == null ? 0L : segment.getSnapshotGrantBeans();
        if (beans > 0) {
            String bizNo = sourceOrderNo == null ? String.valueOf(segment.getId()) : sourceOrderNo;
            beanAccountService.issue(userId, beans, bizNo,
                    "开通会员[" + segment.getLevelName() + "]赠送订阅豆");
        }

        // 新段先入库再按实际履约时间结算：支付在到期前、回调在到期后时，
        // 新段已与旧段无缝相接，结算器能看到后续有效段，因而不会误清会员豆。
        if (grantAt.isAfter(paidAt)) {
            membershipExpiryService.settleUserIfExpired(userId, grantAt);
        }
    }

    @Override
    public boolean hasActiveMembership(Long userId) {
        if (userId == null) {
            return false;
        }
        membershipExpiryService.settleUserIfExpired(userId);
        LocalDateTime now = MembershipPolicy.now();
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<UserMembership>()
                .eq(UserMembership::getUserId, userId)
                .eq(UserMembership::getStatus, STATUS_VALID)
                .le(UserMembership::getStartTime, now)
                .gt(UserMembership::getExpireTime, now));
        return count != null && count > 0;
    }

    @Override
    public void requireActiveMembership(Long userId) {
        if (!hasActiveMembership(userId)) {
            throw new MembershipRequiredException("加油包仅限有效会员购买");
        }
    }

    @Override
    public IPage<MembershipUserVO> pageMembers(String keyword, Long levelId, Integer pageNum, Integer pageSize) {
        LocalDateTime now = MembershipPolicy.now();
        // 1. 查全部当前有效段(status=0 且 start_time<=now<expire_time),按用户分组算当前段
        List<UserMembership> validSegments = list(new LambdaQueryWrapper<UserMembership>()
                .eq(UserMembership::getStatus, STATUS_VALID)
                .le(UserMembership::getStartTime, now)
                .gt(UserMembership::getExpireTime, now));
        Map<Long, UserMembership> currentByUser = currentSegmentMap(validSegments, now);
        if (currentByUser.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }
        // 2. 按当前等级筛选(基于算出的当前段 level_id)
        List<Long> candidateUserIds = currentByUser.entrySet().stream()
                .filter(e -> levelId == null
                        || (e.getValue().getLevelId() != null && e.getValue().getLevelId().equals(levelId)))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        if (candidateUserIds.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }
        // 3. 关键词过滤(昵称/手机号),并取用户基础信息;按创建时间倒序
        LambdaQueryWrapper<MobileUser> userWrapper = new LambdaQueryWrapper<MobileUser>()
                .in(MobileUser::getUserId, candidateUserIds);
        if (keyword != null && !keyword.isEmpty()) {
            userWrapper.and(w -> w.like(MobileUser::getNickName, keyword)
                    .or().like(MobileUser::getPhone, keyword));
        }
        userWrapper.orderByDesc(MobileUser::getCreateTime);
        List<MobileUser> users = mobileUserService.list(userWrapper);

        // 4. 内存分页(会员为后台手动开通,量可控)并组装当前等级
        long total = users.size();
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(users.size(), from + pageSize);
        List<MembershipUserVO> records = new ArrayList<>();
        if (from < to) {
            for (MobileUser user : users.subList(from, to)) {
                UserMembership cur = currentByUser.get(user.getUserId());
                MembershipUserVO vo = new MembershipUserVO();
                vo.setUserId(user.getUserId());
                vo.setUserNo(user.getUserNo());
                vo.setNickName(user.getNickName());
                vo.setPhone(user.getPhone());
                vo.setAvatarUrl(user.getAvatarUrl());
                vo.setCurrentLevelId(cur.getLevelId());
                vo.setCurrentLevelName(cur.getLevelName());
                vo.setCurrentExpireTime(cur.getExpireTime());
                records.add(vo);
            }
        }
        Page<MembershipUserVO> page = new Page<>(pageNum, pageSize, total);
        page.setRecords(records);
        return page;
    }

    @Override
    public Map<Long, UserMembership> currentLevelMapByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        LocalDateTime now = MembershipPolicy.now();
        List<UserMembership> validSegments = list(new LambdaQueryWrapper<UserMembership>()
                .in(UserMembership::getUserId, userIds)
                .eq(UserMembership::getStatus, STATUS_VALID)
                .le(UserMembership::getStartTime, now)
                .gt(UserMembership::getExpireTime, now));
        return currentSegmentMap(validSegments, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustCurrentLevel(Long userId, Long levelId) {
        if (userId == null) {
            throw new ServiceException("用户不能为空");
        }
        MobileUser user = mobileUserService.getById(userId);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }

        MembershipLevel targetLevel = null;
        if (levelId != null) {
            targetLevel = membershipLevelMapper.selectById(levelId);
            if (targetLevel == null) {
                throw new ServiceException("会员等级不存在或已删除");
            }
            if (LEVEL_STATUS_DISABLED.equals(targetLevel.getStatus())) {
                throw new ServiceException("该会员等级已停用,不可选择");
            }
        }

        // 与会员开通、退款、到期结算保持相同锁序，避免交叉操作死锁。
        beanAccountService.lockAccount(userId);
        List<UserMembership> lockedSegments = baseMapper.selectByUserIdForUpdate(userId);
        LocalDateTime now = MembershipPolicy.now();

        if (targetLevel == null) {
            baseMapper.cancelActiveForAdmin(userId, now);
            return;
        }

        List<UserMembership> activeSegments = lockedSegments.stream()
                .filter(segment -> isValid(segment, now))
                .toList();
        if (!activeSegments.isEmpty()) {
            for (UserMembership segment : activeSegments) {
                int updated = baseMapper.adjustActiveLevel(segment.getId(), userId,
                        targetLevel.getLevelId(), targetLevel.getSort(), targetLevel.getName(), now);
                if (updated != 1) {
                    throw new ServiceException("会员等级调整失败");
                }
            }
            return;
        }

        UserMembership segment = new UserMembership();
        segment.setUserId(userId);
        segment.setLevelId(targetLevel.getLevelId());
        segment.setLevelSort(targetLevel.getSort());
        segment.setLevelName(targetLevel.getName());
        segment.setSnapshotGrantBeans(0L);
        segment.setSnapshotPrice(targetLevel.getPrice());
        segment.setSource(SOURCE_MANUAL);
        segment.setStartTime(now);
        segment.setExpireTime(now.plusDays(requireDurationDays(targetLevel.getDurationDays())));
        segment.setStatus(STATUS_VALID);
        segment.setRemark("后台调整会员等级，不发放豆子");
        save(segment);
    }

    @Override
    public UserMembership resolveCurrentSegment(List<UserMembership> segments, LocalDateTime now) {
        if (segments == null || segments.isEmpty()) {
            return null;
        }
        return segments.stream()
                .filter(s -> isValid(s, now))
                .max(Comparator
                        .comparingInt((UserMembership s) -> s.getLevelSort() == null ? Integer.MIN_VALUE : s.getLevelSort())
                        .thenComparing(UserMembership::getExpireTime, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    @Override
    public MembershipInfoVO getCurrentMembership(Long userId) {
        MembershipInfoVO vo = new MembershipInfoVO();
        if (userId == null) {
            // 非会员兜底:剩余天数 0,其余字段 null
            vo.setRemainDays(0);
            return vo;
        }
        // C 端读会员信息时立即结算,避免等待下一轮定时任务。
        membershipExpiryService.settleUserIfExpired(userId);
        LocalDateTime now = MembershipPolicy.now();
        // 查该用户全部会员段(有效性判定交给 resolveCurrentSegment 内部)
        List<UserMembership> segments = list(new LambdaQueryWrapper<UserMembership>()
                .eq(UserMembership::getUserId, userId));
        UserMembership cur = resolveCurrentSegment(segments, now);
        if (cur == null) {
            // 非会员:levelName/expireTime/cardBg 为 null,remainDays=0
            vo.setRemainDays(0);
            return vo;
        }
        vo.setLevelName(cur.getLevelName());
        LocalDateTime coverageExpireTime = resolveContinuousCoverageEnd(segments, now);
        vo.setExpireTime(coverageExpireTime);
        // 剩余天数:向上取整((expireTime-now) 天数);当前段已保证 expireTime>now,过期兜底取 0
        vo.setRemainDays(ceilRemainDays(coverageExpireTime, now));
        // 卡片背景:按当前段 level_id 实时查 membership_level(等级已删则 null)
        if (cur.getLevelId() != null) {
            MembershipLevel level = membershipLevelMapper.selectById(cur.getLevelId());
            if (level != null) {
                vo.setCardBg(level.getCardBg());
            }
        }
        return vo;
    }

    /**
     * 剩余天数向上取整:不足一天按一天计;到期或已过期返回 0。
     */
    private int ceilRemainDays(LocalDateTime expireTime, LocalDateTime now) {
        if (expireTime == null || !expireTime.isAfter(now)) {
            return 0;
        }
        long seconds = Duration.between(now, expireTime).getSeconds();
        return (int) ((seconds + 86399) / 86400);
    }

    /**
     * 按用户分组解析当前段:对传入段集合按 user_id 分组,每组取当前段(level_sort 最高)。
     * 入参既可是预过滤过的有效段、也可是全量段(resolveCurrentSegment 内部再过滤一次有效)。
     */
    private Map<Long, UserMembership> currentSegmentMap(List<UserMembership> segments, LocalDateTime now) {
        Map<Long, List<UserMembership>> byUser = segments.stream()
                .collect(Collectors.groupingBy(UserMembership::getUserId));
        Map<Long, UserMembership> result = new LinkedHashMap<>();
        byUser.forEach((userId, list) -> {
            UserMembership cur = resolveCurrentSegment(list, now);
            if (cur != null) {
                result.put(userId, cur);
            }
        });
        return result;
    }

    /**
     * 从当前有效覆盖向后展开所有首尾相接或重叠的已购续期段，C 端展示整条续费链最终到期日。
     */
    private LocalDateTime resolveContinuousCoverageEnd(List<UserMembership> segments, LocalDateTime now) {
        LocalDateTime coverageEnd = segments.stream()
                .filter(segment -> isValid(segment, now))
                .map(UserMembership::getExpireTime)
                .max(Comparator.naturalOrder())
                .orElse(now);
        boolean extended;
        do {
            LocalDateTime currentEnd = coverageEnd;
            LocalDateTime nextEnd = segments.stream()
                    .filter(segment -> STATUS_VALID.equals(segment.getStatus())
                            && segment.getStartTime() != null
                            && !segment.getStartTime().isAfter(currentEnd)
                            && segment.getExpireTime() != null
                            && segment.getExpireTime().isAfter(currentEnd))
                    .map(UserMembership::getExpireTime)
                    .max(Comparator.naturalOrder())
                    .orElse(currentEnd);
            extended = nextEnd.isAfter(currentEnd);
            coverageEnd = nextEnd;
        } while (extended);
        return coverageEnd;
    }

    /**
     * 当前有效段判定:status=0 且 start_time<=now<expire_time。
     */
    private boolean isValid(UserMembership s, LocalDateTime now) {
        return STATUS_VALID.equals(s.getStatus())
                && s.getStartTime() != null
                && !s.getStartTime().isAfter(now)
                && s.getExpireTime() != null
                && s.getExpireTime().isAfter(now);
    }

    /**
     * 计算新购会员段的生效时间。先找到 paidAt 时刻实际生效且未取消的段，
     * 再沿与它重叠或首尾相接的已购续期段向后扩展到链末。
     * 回调延迟时旧段可能已被标记过期，因此这里只排除「取消」段，
     * 而不依赖履约时的 status=0。
     */
    private LocalDateTime resolveRenewalStart(List<UserMembership> segments,
                                              LocalDateTime paidAt) {
        if (segments == null || segments.isEmpty()) {
            return paidAt;
        }
        List<UserMembership> purchased = segments.stream()
                .filter(segment -> segment != null
                        && !STATUS_CANCELLED.equals(segment.getStatus())
                        && segment.getStartTime() != null
                        && segment.getExpireTime() != null
                        && segment.getExpireTime().isAfter(paidAt)
                        // 回调可能乱序：只用在本单付款时已经购买的段计算续期链。
                        // 例如 A 在 T-1 秒付款但晚回调，B 在 T 付款并先回调；
                        // B 不应把 A 挤到自己之后，否则旧周期的到期清零无法恢复。
                        && !wasPaidAfter(segment, paidAt))
                .toList();
        LocalDateTime chainEnd = purchased.stream()
                .filter(segment -> !segment.getStartTime().isAfter(paidAt))
                .map(UserMembership::getExpireTime)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (chainEnd == null) {
            return paidAt;
        }

        boolean extended;
        do {
            LocalDateTime currentEnd = chainEnd;
            LocalDateTime nextEnd = purchased.stream()
                    .filter(segment -> !segment.getStartTime().isAfter(currentEnd))
                    .map(UserMembership::getExpireTime)
                    .max(Comparator.naturalOrder())
                    .orElse(currentEnd);
            extended = nextEnd.isAfter(currentEnd);
            chainEnd = nextEnd;
        } while (extended);
        return chainEnd;
    }

    private boolean wasPaidAfter(UserMembership segment,
                                 LocalDateTime paidAt) {
        if (!SOURCE_PAY.equals(segment.getSource())
                || segment.getSourceOrderNo() == null
                || segment.getSourceOrderNo().isBlank()) {
            return false;
        }
        LocalDateTime existingPaidAt = segment.getPaidTime();
        // V34 前无法回填真实支付时间的历史段保持旧行为，宁可按已有链续期，不猜测顺序。
        return existingPaidAt != null && existingPaidAt.isAfter(paidAt);
    }

    private void reflowOverlappingFutureSegments(Long userId,
                                                  List<UserMembership> segments,
                                                  LocalDateTime newStart,
                                                  LocalDateTime newExpire,
                                                  LocalDateTime changedAt) {
        if (segments == null || segments.isEmpty()) {
            return;
        }
        LocalDateTime cursor = newExpire;
        List<UserMembership> future = segments.stream()
                .filter(segment -> segment != null
                        && STATUS_VALID.equals(segment.getStatus())
                        && segment.getStartTime() != null
                        && !segment.getStartTime().isBefore(newStart)
                        && segment.getExpireTime() != null)
                .sorted(Comparator.comparing(UserMembership::getStartTime)
                        .thenComparing(UserMembership::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        for (UserMembership segment : future) {
            if (!segment.getStartTime().isBefore(cursor)) {
                break;
            }
            LocalDateTime shiftedStart = cursor;
            Duration originalDuration = Duration.between(
                    segment.getStartTime(), segment.getExpireTime());
            if (originalDuration.isZero() || originalDuration.isNegative()) {
                throw new ServiceException("会员续期段时长异常");
            }
            LocalDateTime shiftedExpire = shiftedStart.plus(originalDuration);
            int updated = baseMapper.rescheduleValidSegment(
                    segment.getId(), userId, shiftedStart, shiftedExpire, changedAt);
            if (updated != 1) {
                throw new ServiceException("会员续期顺序修正失败");
            }
            segment.setStartTime(shiftedStart);
            segment.setExpireTime(shiftedExpire);
            segment.setUpdateTime(changedAt);
            cursor = shiftedExpire;
        }
    }

    /**
     * 定位该次续期链末已经执行过豆子清零的会员段。
     * 只有 renewalStart&gt;paidAt 才表示支付时仍在旧会员链内；
     * 用链末到期时间精确匹配，避免恢复更早、与本次续费无关的到期流水。
     */
    private UserMembership findSettledRenewalTerminal(List<UserMembership> segments,
                                                       LocalDateTime paidAt,
                                                       LocalDateTime renewalStart) {
        if (segments == null || !renewalStart.isAfter(paidAt)) {
            return null;
        }
        return segments.stream()
                .filter(segment -> segment != null
                        && !STATUS_CANCELLED.equals(segment.getStatus())
                        && segment.getExpirySettledTime() != null
                        && renewalStart.equals(segment.getExpireTime()))
                .max(Comparator.comparing(UserMembership::getId,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private int requireDurationDays(Integer durationDays) {
        if (!MembershipPolicy.isValidDurationDays(durationDays)) {
            throw new ServiceException("会员时长必须在1到3650天之间");
        }
        return durationDays;
    }
}
