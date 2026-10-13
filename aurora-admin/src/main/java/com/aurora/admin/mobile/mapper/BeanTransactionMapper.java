package com.aurora.admin.mobile.mapper;

import com.aurora.admin.mobile.domain.BeanTransaction;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 豆子流水 Mapper
 */
@Mapper
public interface BeanTransactionMapper extends BaseMapper<BeanTransaction> {

    /** 账户行锁内按记账顺序读取完整流水，用 FIFO 还原支付订单未消费豆子。 */
    @Select("SELECT * FROM bean_transaction WHERE user_id = #{userId} ORDER BY id")
    List<BeanTransaction> selectLedgerByUserId(@Param("userId") Long userId);

    /**
     * 在 bean_account 行锁内查找同一退款订单的回收流水，作为第二层幂等保护。
     */
    @Select("SELECT * FROM bean_transaction "
            + "WHERE user_id = #{userId} AND type = 'REVOKE' AND biz_no = #{bizNo} "
            + "ORDER BY id DESC LIMIT 1")
    BeanTransaction selectRefundRevoke(@Param("userId") Long userId,
                                       @Param("bizNo") String bizNo);

    /** 按新会员订单号查续费恢复流水，保证同一回调幂等。 */
    @Select("SELECT * FROM bean_transaction "
            + "WHERE user_id = #{userId} AND type = 'RENEW_RESTORE' AND biz_no = #{renewalOrderNo} "
            + "ORDER BY id DESC LIMIT 1")
    BeanTransaction selectRenewalRestoreByOrder(@Param("userId") Long userId,
                                                @Param("renewalOrderNo") String renewalOrderNo);

    /** 按旧到期清零流水查恢复记录，防止两个续费订单重复恢复同一笔余额。 */
    @Select("SELECT * FROM bean_transaction "
            + "WHERE user_id = #{userId} AND type = 'RENEW_RESTORE' AND source_biz_no = #{expiryBizNo} "
            + "ORDER BY id DESC LIMIT 1")
    BeanTransaction selectRenewalRestoreBySource(@Param("userId") Long userId,
                                                 @Param("expiryBizNo") String expiryBizNo);

    /** 查询旧终止会员段实际清零数量。 */
    @Select("SELECT * FROM bean_transaction "
            + "WHERE user_id = #{userId} AND type = 'EXPIRE' AND biz_no = #{expiryBizNo} "
            + "ORDER BY id DESC LIMIT 1")
    BeanTransaction selectMembershipExpiry(@Param("userId") Long userId,
                                           @Param("expiryBizNo") String expiryBizNo);

    /** 查询原始生成任务的扣豆时刻，用于将晚到退豆归属到原会员周期。 */
    @Select("SELECT * FROM bean_transaction "
            + "WHERE user_id = #{userId} AND type = 'DEDUCT' AND biz_no = #{bizNo} "
            + "ORDER BY id DESC LIMIT 1")
    BeanTransaction selectLatestDeduct(@Param("userId") Long userId,
                                       @Param("bizNo") String bizNo);

    /** 查询同一业务号是否已经退还，用于账户行锁内保证退款幂等。 */
    @Select("SELECT * FROM bean_transaction "
            + "WHERE user_id = #{userId} AND type = 'REFUND' AND biz_no = #{bizNo} "
            + "ORDER BY id DESC LIMIT 1")
    BeanTransaction selectLatestRefund(@Param("userId") Long userId,
                                       @Param("bizNo") String bizNo);

    /**
     * 同一会员周期终点需恢复的全部数量：原到期失效 + 回调延迟期间的旧任务退豆失效。
     */
    @Select("SELECT COALESCE(-SUM(change_value), 0) FROM bean_transaction "
            + "WHERE user_id = #{userId} AND type = 'EXPIRE' AND change_value < 0 "
            + "AND (biz_no = #{expiryBizNo} OR source_biz_no = #{expiryBizNo} "
            + "OR source_biz_no LIKE CONCAT(#{expiryBizNo}, '|%'))")
    Long selectExpiredAmountForRenewal(@Param("userId") Long userId,
                                       @Param("expiryBizNo") String expiryBizNo);
}
