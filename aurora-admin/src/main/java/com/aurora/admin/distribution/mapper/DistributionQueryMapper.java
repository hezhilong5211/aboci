package com.aurora.admin.distribution.mapper;

import com.aurora.admin.distribution.vo.DistributionViews;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DistributionQueryMapper {
    DistributionViews.Overview selectOverview();

    List<DistributionViews.WithdrawalRow> selectRecentWithdrawals(@Param("limit") int limit);

    List<DistributionViews.PartnerRow> selectPartnerRanking(@Param("limit") int limit);

    IPage<DistributionViews.PartnerCandidate> selectPartnerCandidates(Page<?> page,
            @Param("keyword") String keyword);

    IPage<DistributionViews.PartnerRow> selectPartnerPage(Page<?> page,
            @Param("phone") String phone,
            @Param("nickName") String nickName,
            @Param("userNo") String userNo,
            @Param("partnerNo") String partnerNo,
            @Param("status") String status);

    DistributionViews.PartnerDetail selectPartnerDetail(@Param("partnerId") Long partnerId);

    IPage<DistributionViews.PromotionUser> selectPromotionUsers(Page<?> page,
            @Param("partnerId") Long partnerId);

    IPage<DistributionViews.PromotionOrder> selectPromotionOrders(Page<?> page,
            @Param("partnerId") Long partnerId);

    IPage<DistributionViews.CommissionRow> selectCommissionPage(Page<?> page,
            @Param("partnerKeyword") String partnerKeyword,
            @Param("phone") String phone,
            @Param("orderNo") String orderNo,
            @Param("productType") String productType,
            @Param("status") String status,
            @Param("payStart") LocalDateTime payStart,
            @Param("payEnd") LocalDateTime payEnd,
            @Param("partnerId") Long partnerId);

    DistributionViews.CommissionDetail selectCommissionDetail(@Param("commissionId") Long commissionId);

    List<DistributionViews.TransactionRow> selectTransactionsByOrder(@Param("orderNo") String orderNo);

    List<DistributionViews.TransactionRow> selectTransactionsByWithdrawal(@Param("withdrawalNo") String withdrawalNo);

    IPage<DistributionViews.WithdrawalRow> selectWithdrawalPage(Page<?> page,
            @Param("withdrawalNo") String withdrawalNo,
            @Param("partnerKeyword") String partnerKeyword,
            @Param("phone") String phone,
            @Param("methodType") String methodType,
            @Param("status") String status,
            @Param("applyStart") LocalDateTime applyStart,
            @Param("applyEnd") LocalDateTime applyEnd,
            @Param("partnerId") Long partnerId);

    DistributionViews.WithdrawalDetail selectWithdrawalDetail(@Param("withdrawalId") Long withdrawalId);

    DistributionViews.Dashboard selectDashboard(@Param("partnerId") Long partnerId);
}
