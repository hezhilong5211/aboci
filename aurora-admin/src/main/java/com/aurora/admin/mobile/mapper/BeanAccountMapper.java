package com.aurora.admin.mobile.mapper;

import com.aurora.admin.mobile.domain.BeanAccount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 豆子账户 Mapper
 * 余额变动一律走自定义原子 UPDATE(单条 SQL 内完成判断+更新),禁止读改写,杜绝并发超扣。
 */
@Mapper
public interface BeanAccountMapper extends BaseMapper<BeanAccount> {

    /** 按用户锁定账户行，作为会员开通、到期结算、充值与扣费的用户级串行化锁。 */
    @Select("SELECT * FROM bean_account WHERE user_id = #{userId} FOR UPDATE")
    BeanAccount selectByUserIdForUpdate(@Param("userId") Long userId);

    /**
     * 原子加豆:balance += amount、total_earned += amount。
     * 用于赠送/发放/充值,影响 1 行视为成功。
     *
     * @param userId 用户ID
     * @param amount 加豆数量(>0)
     * @return 影响行数
     */
    @Update("UPDATE bean_account SET balance = balance + #{amount}, total_earned = total_earned + #{amount} "
            + "WHERE user_id = #{userId}")
    int increaseEarn(@Param("userId") Long userId, @Param("amount") long amount);

    /**
     * 原子扣减:balance -= amount、total_consumed += amount,且仅当 balance >= amount 才更新。
     * 影响 0 行表示余额不足(由调用方抛出业务异常),从根本上杜绝并发超扣。
     *
     * @param userId 用户ID
     * @param amount 扣减数量(>0)
     * @return 影响行数(0 表示余额不足或账户不存在)
     */
    @Update("UPDATE bean_account SET balance = balance - #{amount}, total_consumed = total_consumed + #{amount} "
            + "WHERE user_id = #{userId} AND balance >= #{amount}")
    int deductIfEnough(@Param("userId") Long userId, @Param("amount") long amount);

    /**
     * 原子退还:balance += amount、total_consumed = GREATEST(total_consumed - amount, 0)(不为负)。
     *
     * @param userId 用户ID
     * @param amount 退还数量(>0)
     * @return 影响行数
     */
    @Update("UPDATE bean_account SET balance = balance + #{amount}, "
            + "total_consumed = GREATEST(total_consumed - #{amount}, 0) "
            + "WHERE user_id = #{userId}")
    int refund(@Param("userId") Long userId, @Param("amount") long amount);

    /**
     * 原子清空余额并累计到 total_expired；调用前必须已锁定账户行。
     * MySQL SET 从左到右求值，因此必须先累计旧 balance，再把 balance 置 0。
     */
    @Update("UPDATE bean_account SET total_expired = total_expired + balance, balance = 0, update_time = NOW() "
            + "WHERE user_id = #{userId} AND balance > 0")
    int clearForMembershipExpiry(@Param("userId") Long userId);

    /** 到期结算后仅回收一笔晚到退款对应的豆子，不影响账户内其他合法新到账。 */
    @Update("UPDATE bean_account SET balance = balance - #{amount}, "
            + "total_expired = total_expired + #{amount}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND balance >= #{amount}")
    int expireAmount(@Param("userId") Long userId, @Param("amount") long amount);

    /**
     * 退款权益回收：仅扣减调用方在账户行锁内计算好的实际数量，
     * 单独累计到 total_revoked，不混入真实消费或会员到期数据。
     */
    @Update("UPDATE bean_account SET balance = balance - #{amount}, "
            + "total_revoked = total_revoked + #{amount}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND balance >= #{amount}")
    int revokeForOrderRefund(@Param("userId") Long userId, @Param("amount") long amount);

    /** 恢复连续续费前被到期任务清零的数量；调用前必须持有账户行锁。 */
    @Update("UPDATE bean_account SET balance = balance + #{amount}, "
            + "total_expired = total_expired - #{amount}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND total_expired >= #{amount}")
    int restoreExpiredForRenewal(@Param("userId") Long userId, @Param("amount") long amount);
}
