package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.BeanAccount;
import com.aurora.admin.mobile.vo.BeanAccountVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Collection;
import java.util.Map;

/**
 * 豆子账户服务(豆子余额唯一写入路径)
 * 任何余额变动必须经本服务,禁止旁路改余额;每次变动同事务写流水(含余额快照)。
 */
public interface IBeanAccountService extends IService<BeanAccount> {

    /**
     * 确保账户存在:无则建零余额账户(幂等)。
     *
     * @param userId 用户ID
     */
    void ensureAccount(Long userId);

    /**
     * 确保账户存在并执行 SELECT ... FOR UPDATE，作为该用户余额与会员生命周期的事务锁。
     */
    BeanAccount lockAccount(Long userId);

    /**
     * 赠送豆子:balance += amount、total_earned += amount,记 GIFT 流水。
     *
     * @param userId 用户ID
     * @param amount 数量(>0)
     * @param bizNo  关联业务单号(可空)
     * @param remark 备注
     */
    void grant(Long userId, long amount, String bizNo, String remark);

    /**
     * 发放豆子:balance += amount、total_earned += amount,记 ISSUE 流水。
     *
     * @param userId 用户ID
     * @param amount 数量(>0)
     * @param bizNo  关联业务单号(可空)
     * @param remark 备注
     */
    void issue(Long userId, long amount, String bizNo, String remark);

    /**
     * 充值豆子:balance += amount、total_earned += amount,记 RECHARGE 流水。
     *
     * @param userId 用户ID
     * @param amount 数量(>0)
     * @param bizNo  关联业务单号(可空)
     * @param remark 备注
     */
    void recharge(Long userId, long amount, String bizNo, String remark);

    /**
     * 扣费:原子扣减,余额不足抛 ServiceException("豆子余额不足"),记 DEDUCT 流水。
     *
     * @param userId 用户ID
     * @param amount 数量(>0)
     * @param bizNo  关联业务单号(可空)
     * @param remark 备注
     */
    void deduct(Long userId, long amount, String bizNo, String remark);

    /**
     * 退还:balance += amount、total_consumed -= amount(不为负),记 REFUND 流水。
     *
     * @param userId 用户ID
     * @param amount 数量(>0)
     * @param bizNo  关联业务单号(可空)
     * @param remark 备注
     */
    void refund(Long userId, long amount, String bizNo, String remark);

    /**
     * 后台手动调整:delta>0 走加、delta<0 走原子扣减校验,记 ADJUST 流水。
     *
     * @param userId 用户ID
     * @param delta  调整量(≠0,正加负减)
     * @param remark 备注
     */
    void adjust(Long userId, long delta, String remark);

    /**
     * 清空已锁定用户的全部余额并写 EXPIRE 流水；余额已为0时幂等返回0且不重复写流水。
     */
    long clearForMembershipExpiry(Long userId, String bizNo, String remark);

    /**
     * 仅失效尚未消费的会员赠豆(ISSUE)，加油包充值豆及其他永久豆不受影响。
     * {@code maxAmount} 用于限制旧任务晚到退豆场景的本次最大失效数量。
     */
    long expireMembershipGrantBalance(Long userId, long maxAmount, String bizNo,
                                      String sourceBizNo, String remark);

    /** 仅将指定数量转为到期豆并写 EXPIRE 流水，不清理账户中的其他余额。 */
    long expireAmount(Long userId, long amount, String bizNo, String remark);

    /**
     * 仅将指定数量转为到期豆，并将本次过期关联到真正的会员周期终点。
     * <p>
     * 用于“旧周期任务晚到退豆”：{@code bizNo} 是本次补偿结算单，
     * {@code sourceBizNo} 以 {@code membership-expire:<id>} 开头并可携带任务明细。若后续证实该周期是
     * 到期前已付款的连续续费，RENEW_RESTORE 可按该来源精确恢复。
     */
    long expireAmount(Long userId, long amount, String bizNo, String sourceBizNo, String remark);

    /**
     * 原任务消耗的豆批次已被平台会员/加油包退款 REVOKE 后，
     * 任务才失败退豆时，只回收还原到已取消批次的部分。
     * 后续新会员或新充值批次不受影响。
     */
    long revokeCancelledTaskRefund(Long userId, long amount, String taskBizNo,
                                   String bizNo, String remark);

    /**
     * 全额退款时回收该订单已发放的指定数量豆子。
     * <p>
     * 实际回收该订单按流水还原后的未消费部分，并受当前余额上限保护，绝不追扣后来新豆；
     * {@code bizNo} 必填，同一订单的重复通知只返回首次结果，不会再次扣减。
     * 无论实际回收是否为 0，都会写入 REVOKE 审计流水。
     */
    BeanRefundRevokeResult revokeForOrderRefund(Long userId, long requested,
                                                 String bizNo, String remark);

    /**
     * 兼容旧流程：回收账户全部余额。新会员退款流程不得调用，避免清除永久有效的加油包豆。
     * 同样以 {@code bizNo} 保证幂等，并写入 REVOKE 审计流水。
     */
    BeanRefundRevokeResult revokeAllForOrderRefund(Long userId, String bizNo, String remark);

    /**
     * 续费付款发生在旧会员到期前，但支付回调到达前旧余额已被到期任务清零时，
     * 按旧终止会员段的 EXPIRE 流水恢复实际清零数量。
     * <p>
     * 以 {@code renewalOrderNo} 保证同一续费通知幂等，并以 {@code expiryBizNo}
     * 保证同一次到期清零最多被恢复一次。
     */
    BeanRenewalRestoreResult restoreExpiredForRenewal(Long userId, String expiryBizNo,
                                                       String renewalOrderNo, String remark);

    /**
     * 查询账户余额视图(无账户视为全 0)。
     *
     * @param userId 用户ID
     * @return 账户视图
     */
    BeanAccountVO getAccount(Long userId);

    /**
     * 批量查询多个用户的余额(用于后台列表填充 balance 列,避免 N+1)。
     * 无账户的用户不在返回 Map 中,由调用方按需兜底为 0。
     *
     * @param userIds 用户ID集合
     * @return userId → balance 映射
     */
    Map<Long, Long> balanceMapByUserIds(Collection<Long> userIds);
}
