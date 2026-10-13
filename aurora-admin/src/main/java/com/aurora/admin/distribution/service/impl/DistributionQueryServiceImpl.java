package com.aurora.admin.distribution.service.impl;

import com.aurora.admin.distribution.mapper.DistributionQueryMapper;
import com.aurora.admin.distribution.service.IDistributionQueryService;
import com.aurora.admin.distribution.vo.DistributionViews;
import com.aurora.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DistributionQueryServiceImpl implements IDistributionQueryService {

    private final DistributionQueryMapper queryMapper;

    @Override
    public DistributionViews.Overview overview() {
        DistributionViews.Overview overview = queryMapper.selectOverview();
        overview.setRecentWithdrawals(queryMapper.selectRecentWithdrawals(8));
        overview.setRanking(queryMapper.selectPartnerRanking(10));
        return overview;
    }

    @Override
    public IPage<DistributionViews.PartnerCandidate> partnerCandidates(int pageNum, int pageSize, String keyword) {
        return queryMapper.selectPartnerCandidates(page(pageNum, pageSize), trim(keyword));
    }

    @Override
    public IPage<DistributionViews.PartnerRow> partners(int pageNum, int pageSize, String phone, String nickName,
            String userNo, String partnerNo, String status) {
        return queryMapper.selectPartnerPage(page(pageNum, pageSize), trim(phone), trim(nickName), trim(userNo),
                trim(partnerNo), trim(status));
    }

    @Override
    public DistributionViews.PartnerDetail partnerDetail(Long partnerId) {
        DistributionViews.PartnerDetail detail = queryMapper.selectPartnerDetail(partnerId);
        if (detail == null) {
            throw new ServiceException("分销商不存在");
        }
        return detail;
    }

    @Override
    public IPage<DistributionViews.PromotionUser> promotionUsers(Long partnerId, int pageNum, int pageSize) {
        return queryMapper.selectPromotionUsers(page(pageNum, pageSize), partnerId);
    }

    @Override
    public IPage<DistributionViews.PromotionOrder> promotionOrders(Long partnerId, int pageNum, int pageSize) {
        return queryMapper.selectPromotionOrders(page(pageNum, pageSize), partnerId);
    }

    @Override
    public IPage<DistributionViews.CommissionRow> commissions(int pageNum, int pageSize, String partnerKeyword,
            String phone, String orderNo, String productType, String status,
            LocalDateTime payStart, LocalDateTime payEnd, Long partnerId) {
        return queryMapper.selectCommissionPage(page(pageNum, pageSize), trim(partnerKeyword), trim(phone),
                trim(orderNo), trim(productType), trim(status), payStart, payEnd, partnerId);
    }

    @Override
    public DistributionViews.CommissionDetail commissionDetail(Long commissionId) {
        DistributionViews.CommissionDetail detail = queryMapper.selectCommissionDetail(commissionId);
        if (detail == null) {
            throw new ServiceException("佣金记录不存在");
        }
        detail.setTransactions(queryMapper.selectTransactionsByOrder(detail.getOrderNo()));
        return detail;
    }

    @Override
    public IPage<DistributionViews.WithdrawalRow> withdrawals(int pageNum, int pageSize, String withdrawalNo,
            String partnerKeyword, String phone, String methodType, String status,
            LocalDateTime applyStart, LocalDateTime applyEnd, Long partnerId) {
        return queryMapper.selectWithdrawalPage(page(pageNum, pageSize), trim(withdrawalNo), trim(partnerKeyword),
                trim(phone), trim(methodType), trim(status), applyStart, applyEnd, partnerId);
    }

    @Override
    public DistributionViews.WithdrawalDetail withdrawalDetail(Long withdrawalId, Long ownerPartnerId) {
        DistributionViews.WithdrawalDetail detail = queryMapper.selectWithdrawalDetail(withdrawalId);
        if (detail == null || (ownerPartnerId != null && !ownerPartnerId.equals(detail.getPartnerId()))) {
            throw new ServiceException("提现记录不存在", 404);
        }
        detail.setTransactions(queryMapper.selectTransactionsByWithdrawal(detail.getWithdrawalNo()));
        return detail;
    }

    @Override
    public DistributionViews.Dashboard dashboard(Long partnerId) {
        DistributionViews.Dashboard dashboard = queryMapper.selectDashboard(partnerId);
        if (dashboard == null) {
            throw new ServiceException("分销商不存在");
        }
        return dashboard;
    }

    private Page<?> page(int pageNum, int pageSize) {
        long current = Math.max(1, pageNum);
        long size = Math.min(100, Math.max(1, pageSize));
        return new Page<>(current, size);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
