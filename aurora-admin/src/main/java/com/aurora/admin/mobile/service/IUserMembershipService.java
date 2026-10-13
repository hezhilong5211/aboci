package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.UserMembership;
import com.aurora.admin.mobile.vo.MembershipInfoVO;
import com.aurora.admin.mobile.vo.MembershipUserVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 用户会员段 Service 接口(叠加制开通 + 当前等级判定 + 会员列表)
 */
public interface IUserMembershipService extends IService<UserMembership> {

    /**
     * 手动开通会员(叠加制):先结算已到期权益,再查等级取快照 →
     * 按等级当前配置新建一条有效段(不动旧记录)→ 调账户服务发订阅豆。
     *
     * @param userId       用户ID
     * @param levelId      等级ID
     * @param durationDays 为兼容旧接口保留,实际以等级当前配置为准
     */
    void grant(Long userId, Long levelId, Integer durationDays);

    /**
     * 支付成功后按下单快照发放会员，不再读取可能已被修改或删除的等级配置。
     * sourceOrderNo 在数据库唯一，作为订单行锁之外的第二层幂等保护。
     * durationDays 是下单时固化的会员有效天数。
     */
    void grantFromPayment(Long userId, Long levelId, Integer levelSort, String levelName,
                          Long grantBeans, BigDecimal price, Integer durationDays,
                          String sourceOrderNo);

    /**
     * 支付履约时显式传入支付时间。会员是否续期只以 paidAt 判定，
     * 不受微信回调延迟或服务器补单时间影响。
     */
    void grantFromPayment(Long userId, Long levelId, Integer levelSort, String levelName,
                          Long grantBeans, BigDecimal price, Integer durationDays,
                          String sourceOrderNo, LocalDateTime paidAt);

    /**
     * 支付履约的完整时间语义。
     *
     * <p>若 paidAt 处在未取消的已购会员链内，新段从该链末到期时间开始；
     * 若 paidAt 时已过期，新段从 paidAt 开始。grantAt 是实际履约/补单时间，
     * 新段入库后再结算截止该时刻的已到期权益，避免延迟回调误清续期权益。</p>
     */
    void grantFromPayment(Long userId, Long levelId, Integer levelSort, String levelName,
                          Long grantBeans, BigDecimal price, Integer durationDays,
                          String sourceOrderNo, LocalDateTime paidAt, LocalDateTime grantAt);

    /**
     * 判断用户当前是否有有效会员段。查询前会先执行该用户的到期惰性结算。
     *
     * @param userId 用户ID
     * @return status=0 且 start_time&lt;=now&lt;expire_time 时返回 true
     */
    boolean hasActiveMembership(Long userId);

    /**
     * 要求用户必须是有效会员,用于加油包下单与履约校验。
     *
     * @throws com.aurora.common.core.exception.ServiceException 非有效会员时抛出统一业务提示
     */
    void requireActiveMembership(Long userId);

    /**
     * 分页查询会员列表(只列当前有效会员;每行带当前等级 = 有效段中 level_sort 最高的快照)。
     *
     * @param keyword  关键词(匹配 mobile_user 昵称/手机号,可空)
     * @param levelId  按当前等级ID筛选(可空)
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 会员列表分页
     */
    IPage<MembershipUserVO> pageMembers(String keyword, Long levelId, Integer pageNum, Integer pageSize);

    /**
     * 批量计算多个用户的当前等级名(有效段中 level_sort 最高的快照等级名),无有效段则不在返回中。
     * 供移动端用户列表批量填充「会员等级」列,避免 N+1。
     *
     * @param userIds 用户ID集合
     * @return userId → 当前等级名 映射
     */
    Map<Long, UserMembership> currentLevelMapByUserIds(Collection<Long> userIds);

    /**
     * 后台调整用户当前会员等级。
     * 有当前会员时仅修改当前生效段的等级快照，不改有效期、不发豆；
     * 无当前会员时按目标等级当前配置创建零赠豆段。
     * levelId 为空时仅取消当前时刻生效的会员段，历史和未来会员段保持不变。
     *
     * @param userId 用户ID
     * @param levelId 目标等级ID；null 表示无会员
     */
    void adjustCurrentLevel(Long userId, Long levelId);

    /**
     * 从某用户的会员段集合中解析「当前段」:过滤有效段(status=0 且 start_time&lt;=now&lt;expire_time),
     * 取 level_sort 最高的一条(并列时取到期更晚的);无有效段返回 null。
     * 提取为独立方法以便单测:快照兜底、叠加取最高、过期段不计入。
     *
     * @param segments 该用户的会员段集合
     * @param now      判定基准时间
     * @return 当前段;无有效段返回 null
     */
    UserMembership resolveCurrentSegment(List<UserMembership> segments, LocalDateTime now);

    /**
     * C 端:查询某用户当前会员信息。查询前先执行该用户的到期惰性结算。
     * 查该用户全部会员段 → resolveCurrentSegment 取当前有效段:
     *   无有效段 → levelName=null(非会员),expireTime=null、remainDays=0、cardBg=null;
     *   有有效段 → levelName/expireTime 取当前段快照,remainDays=向上取整((expireTime-now) 天数,过期取0),
     *     cardBg 按当前段 level_id 查 membership_level 取 card_bg(等级已删则 null)。
     *
     * @param userId 用户ID
     * @return 当前会员信息(始终非 null,非会员各字段为空/0)
     */
    MembershipInfoVO getCurrentMembership(Long userId);
}
