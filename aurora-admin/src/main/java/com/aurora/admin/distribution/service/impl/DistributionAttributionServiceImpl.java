package com.aurora.admin.distribution.service.impl;

import com.aurora.admin.distribution.domain.DistributionConfig;
import com.aurora.admin.distribution.domain.DistributionPartner;
import com.aurora.admin.distribution.domain.DistributionRelation;
import com.aurora.admin.distribution.domain.DistributionVisit;
import com.aurora.admin.distribution.dto.DistributionDtos;
import com.aurora.admin.distribution.mapper.DistributionPartnerMapper;
import com.aurora.admin.distribution.mapper.DistributionRelationMapper;
import com.aurora.admin.distribution.mapper.DistributionVisitMapper;
import com.aurora.admin.distribution.service.IDistributionAttributionService;
import com.aurora.admin.distribution.service.IDistributionPartnerService;
import com.aurora.admin.distribution.support.DistributionConstants;
import com.aurora.admin.distribution.vo.DistributionViews;
import com.aurora.admin.mobile.domain.MobileUser;
import com.aurora.admin.mobile.mapper.MobileUserMapper;
import com.aurora.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DistributionAttributionServiceImpl implements IDistributionAttributionService {

    private final IDistributionPartnerService partnerService;
    private final DistributionPartnerMapper partnerMapper;
    private final DistributionRelationMapper relationMapper;
    private final DistributionVisitMapper visitMapper;
    private final MobileUserMapper mobileUserMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordVisit(DistributionDtos.Visit request) {
        DistributionConfig config = partnerService.getConfig();
        if (config.getEnabled() != 1 || visitMapper.selectByEventId(request.getEventId()) != null) {
            return;
        }
        DistributionPartner partner = partnerMapper.selectByInviteCode(request.getInviteCode());
        if (partner == null || !DistributionConstants.PARTNER_ACTIVE.equals(partner.getStatus())) {
            return;
        }

        DistributionVisit visit = new DistributionVisit();
        visit.setEventId(request.getEventId());
        visit.setPartnerId(partner.getPartnerId());
        visit.setLaunchScene(request.getLaunchScene() == null ? "" : request.getLaunchScene().trim());
        visit.setBound(0);
        visit.setVisitTime(LocalDateTime.now());
        try {
            visitMapper.insert(visit);
        } catch (DuplicateKeyException ignored) {
            // 同一启动事件重复上报按成功处理。
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DistributionViews.BindResult bind(Long userId, DistributionDtos.Bind request) {
        DistributionRelation existing = relationMapper.selectByReferredUserId(userId);
        if (existing != null) {
            return existingResult(existing);
        }

        DistributionConfig config = partnerService.getConfig();
        if (config.getEnabled() != 1) {
            throw new ServiceException("分销功能暂未开启");
        }
        DistributionPartner partner = partnerMapper.selectByInviteCode(request.getInviteCode());
        if (partner == null || !DistributionConstants.PARTNER_ACTIVE.equals(partner.getStatus())) {
            throw new ServiceException("邀请码无效或分销商已停用");
        }
        if (partner.getUserId().equals(userId)) {
            // 自扫不建立关系，但必须返回可清理的终态；否则客户端会永久保留 pending，
            // 每次启动重复绑定，并把瞬时鉴权失败误表现为“分销身份消失”。
            return currentPartnerResult(partner);
        }

        DistributionVisit visit = visitMapper.selectByEventIdForUpdate(request.getEventId());
        if (visit == null || !visit.getPartnerId().equals(partner.getPartnerId())) {
            throw new ServiceException("访问事件无效，请重新扫描二维码");
        }

        MobileUser user = mobileUserMapper.selectById(userId);
        if (user == null || !"0".equals(user.getStatus())) {
            throw new ServiceException("当前小程序用户不可用");
        }

        LocalDateTime now = LocalDateTime.now();
        DistributionRelation relation = new DistributionRelation();
        relation.setPartnerId(partner.getPartnerId());
        relation.setReferredUserId(userId);
        relation.setInviteCode(partner.getInviteCode());
        relation.setFirstVisitTime(visit.getVisitTime());
        relation.setBindTime(now);
        relation.setExistingUser(user.getCreateTime() != null && !user.getCreateTime().isAfter(visit.getVisitTime()) ? 1 : 0);
        relation.setStatus(DistributionConstants.PARTNER_ACTIVE);
        relation.setCreateTime(now);
        try {
            relationMapper.insert(relation);
        } catch (DuplicateKeyException ignored) {
            DistributionRelation concurrentlyBound = relationMapper.selectByReferredUserId(userId);
            if (concurrentlyBound == null) {
                throw new ServiceException("绑定失败，请重试");
            }
            return existingResult(concurrentlyBound);
        }

        visit.setUserId(userId);
        visit.setBound(1);
        visit.setBindTime(now);
        visitMapper.updateById(visit);

        DistributionViews.BindResult result = new DistributionViews.BindResult();
        result.setBound(true);
        result.setAlreadyBound(false);
        result.setPartnerNo(partner.getPartnerNo());
        return result;
    }

    private DistributionViews.BindResult existingResult(DistributionRelation relation) {
        DistributionPartner partner = partnerMapper.selectById(relation.getPartnerId());
        DistributionViews.BindResult result = new DistributionViews.BindResult();
        result.setBound(true);
        result.setAlreadyBound(true);
        result.setPartnerNo(partner == null ? null : partner.getPartnerNo());
        return result;
    }

    private DistributionViews.BindResult currentPartnerResult(DistributionPartner partner) {
        DistributionViews.BindResult result = new DistributionViews.BindResult();
        result.setBound(true);
        result.setAlreadyBound(true);
        result.setPartnerNo(partner.getPartnerNo());
        return result;
    }
}
