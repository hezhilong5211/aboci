package com.aurora.admin.mobile.mapper;

import com.aurora.admin.mobile.domain.UserMembership;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户会员段 Mapper
 */
@Mapper
public interface UserMembershipMapper extends BaseMapper<UserMembership> {

    /**
     * 到期候选用户：包含尚未标记的到期段，以及历史数据中最新到期段尚未完成一次性结算的用户。
     */
    @Select("""
            SELECT DISTINCT um.user_id
            FROM user_membership um
            WHERE (um.status = '0' AND um.expire_time <= #{cutoff})
               OR (um.status = '1' AND um.expire_time <= #{cutoff} AND um.expiry_settled_time IS NULL
                   AND NOT EXISTS (
                       SELECT 1 FROM user_membership active
                       WHERE active.user_id = um.user_id
                         AND active.status = '0'
                         AND active.start_time <= #{cutoff}
                         AND active.expire_time > #{cutoff}
                   )
                   AND NOT EXISTS (
                       SELECT 1 FROM user_membership newer
                       WHERE newer.user_id = um.user_id
                         AND newer.status IN ('0', '1')
                         AND newer.expire_time <= #{cutoff}
                         AND (newer.expire_time > um.expire_time
                              OR (newer.expire_time = um.expire_time AND newer.id > um.id))
                   ))
            ORDER BY um.user_id
            LIMIT #{limit}
            """)
    List<Long> selectExpiryCandidateUserIds(@Param("cutoff") LocalDateTime cutoff,
                                             @Param("limit") int limit);

    /** 锁定该用户全部会员段；锁序统一为 bean_account → user_membership。 */
    @Select("SELECT * FROM user_membership WHERE user_id = #{userId} ORDER BY id FOR UPDATE")
    List<UserMembership> selectByUserIdForUpdate(@Param("userId") Long userId);

    /** 将截止时间前仍标记有效的会员段更新为已过期。 */
    @Update("UPDATE user_membership SET status = '1', update_time = NOW() "
            + "WHERE user_id = #{userId} AND status = '0' AND expire_time <= #{cutoff}")
    int markExpired(@Param("userId") Long userId, @Param("cutoff") LocalDateTime cutoff);

    /** 标记最后一个到期段已完成一次性豆子清零结算。 */
    @Update("UPDATE user_membership SET expiry_settled_time = #{settledTime}, update_time = NOW() "
            + "WHERE id = #{id} AND expiry_settled_time IS NULL")
    int markExpirySettled(@Param("id") Long id, @Param("settledTime") LocalDateTime settledTime);

    /**
     * 按会员段主键和支付订单号精确取消。调用前已按用户锁定全部会员段。
     */
    @Update("UPDATE user_membership SET status = '2', update_time = #{refundAt} "
            + "WHERE id = #{id} AND source_order_no = #{sourceOrderNo} AND status <> '2'")
    int cancelForRefund(@Param("id") Long id,
                        @Param("sourceOrderNo") String sourceOrderNo,
                        @Param("refundAt") LocalDateTime refundAt);

    /** 调整有效会员段起止时间，用于退款链压缩及延迟支付回调的重叠链修正。 */
    @Update("UPDATE user_membership SET start_time = #{startTime}, expire_time = #{expireTime}, "
            + "update_time = #{refundAt} "
            + "WHERE id = #{id} AND user_id = #{userId} AND status = '0'")
    int rescheduleValidSegment(@Param("id") Long id,
                               @Param("userId") Long userId,
                               @Param("startTime") LocalDateTime startTime,
                               @Param("expireTime") LocalDateTime expireTime,
                               @Param("refundAt") LocalDateTime refundAt);

    /** 后台把当前生效段调整为指定等级，不改变期限、支付快照和赠豆快照。 */
    @Update("UPDATE user_membership SET level_id = #{levelId}, level_sort = #{levelSort}, "
            + "level_name = #{levelName}, update_time = #{changedAt} "
            + "WHERE id = #{id} AND user_id = #{userId} AND status = '0' "
            + "AND start_time <= #{changedAt} AND expire_time > #{changedAt}")
    int adjustActiveLevel(@Param("id") Long id,
                          @Param("userId") Long userId,
                          @Param("levelId") Long levelId,
                          @Param("levelSort") Integer levelSort,
                          @Param("levelName") String levelName,
                          @Param("changedAt") LocalDateTime changedAt);

    /** 后台取消当前时刻生效的全部会员段；未来续期段不受影响。 */
    @Update("UPDATE user_membership SET status = '2', update_time = #{changedAt} "
            + "WHERE user_id = #{userId} AND status = '0' "
            + "AND start_time <= #{changedAt} AND expire_time > #{changedAt}")
    int cancelActiveForAdmin(@Param("userId") Long userId,
                             @Param("changedAt") LocalDateTime changedAt);
}
