package com.aurora.admin.distribution.service.impl;

import com.aurora.admin.distribution.domain.DistributionAccount;
import com.aurora.admin.distribution.domain.DistributionConfig;
import com.aurora.admin.distribution.domain.DistributionPartner;
import com.aurora.admin.distribution.dto.DistributionDtos;
import com.aurora.admin.distribution.mapper.DistributionAccountMapper;
import com.aurora.admin.distribution.mapper.DistributionConfigMapper;
import com.aurora.admin.distribution.mapper.DistributionPartnerMapper;
import com.aurora.admin.distribution.service.IDistributionPartnerService;
import com.aurora.admin.distribution.support.DistributionConstants;
import com.aurora.admin.distribution.vo.DistributionViews;
import com.aurora.admin.mobile.domain.MobileUser;
import com.aurora.admin.mobile.mapper.MobileUserMapper;
import com.aurora.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class DistributionPartnerServiceImpl implements IDistributionPartnerService {

    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DistributionPartnerMapper partnerMapper;
    private final DistributionAccountMapper accountMapper;
    private final DistributionConfigMapper configMapper;
    private final MobileUserMapper mobileUserMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addPartner(DistributionDtos.PartnerAdd request, String operator) {
        MobileUser user = mobileUserMapper.selectById(request.getUserId());
        if (user == null || !"0".equals(user.getStatus())) {
            throw new ServiceException("小程序用户不存在或已停用");
        }
        if (partnerMapper.selectByUserId(request.getUserId()) != null) {
            throw new ServiceException("该用户已经是分销商");
        }

        LocalDateTime now = LocalDateTime.now();
        DistributionPartner partner = new DistributionPartner();
        partner.setUserId(user.getUserId());
        partner.setPartnerNo(generatePartnerNo());
        partner.setInviteCode(generateInviteCode());
        partner.setStatus(DistributionConstants.PARTNER_ACTIVE);
        partner.setEnabledTime(now);
        partner.setRemark(normalize(request.getRemark()));
        partner.setCreateBy(operator);
        partner.setCreateTime(now);
        partner.setUpdateBy(operator);
        partner.setUpdateTime(now);
        partner.setDelFlag("0");
        partnerMapper.insert(partner);

        DistributionAccount account = new DistributionAccount();
        account.setPartnerId(partner.getPartnerId());
        account.setAvailableAmount(0L);
        account.setWithdrawLockedAmount(0L);
        account.setTotalWithdrawn(0L);
        account.setTotalReversed(0L);
        account.setDebtAmount(0L);
        account.setVersion(0);
        account.setUpdateTime(now);
        accountMapper.insert(account);
        return partner.getPartnerId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long partnerId, DistributionDtos.PartnerStatus request, String operator) {
        DistributionPartner partner = partnerMapper.selectByIdForUpdate(partnerId);
        if (partner == null) {
            throw new ServiceException("分销商不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!request.getStatus().equals(partner.getStatus())) {
            partner.setStatus(request.getStatus());
            if (DistributionConstants.PARTNER_ACTIVE.equals(request.getStatus())) {
                partner.setEnabledTime(now);
                partner.setDisabledTime(null);
            } else {
                partner.setDisabledTime(now);
            }
        }
        partner.setRemark(normalize(request.getRemark()));
        partner.setUpdateBy(operator);
        partner.setUpdateTime(now);
        partnerMapper.updateById(partner);
    }

    @Override
    public DistributionConfig getConfig() {
        DistributionConfig config = configMapper.selectById(DistributionConstants.CONFIG_ID);
        if (config == null) {
            throw new ServiceException("分销配置不存在");
        }
        return config;
    }

    @Override
    public DistributionViews.ConfigView getConfigView() {
        DistributionConfig config = getConfig();
        DistributionViews.ConfigView view = new DistributionViews.ConfigView();
        view.setEnabled(config.getEnabled());
        view.setCommissionRateBps(config.getCommissionRateBps());
        view.setMinWithdrawAmount(config.getMinWithdrawAmount());
        view.setUpdateBy(config.getUpdateBy());
        view.setUpdateTime(config.getUpdateTime());
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(DistributionDtos.ConfigUpdate request, String operator) {
        DistributionConfig config = configMapper.selectForUpdate();
        if (config == null) {
            throw new ServiceException("分销配置不存在");
        }
        if (request.getEnabled() == 1
                && (request.getCommissionRateBps() <= 0 || request.getMinWithdrawAmount() <= 0)) {
            throw new ServiceException("启用分销前必须配置有效佣金比例和最低提现金额");
        }
        config.setEnabled(request.getEnabled());
        config.setCommissionRateBps(request.getCommissionRateBps());
        config.setMinWithdrawAmount(request.getMinWithdrawAmount());
        config.setUpdateBy(operator);
        config.setUpdateTime(LocalDateTime.now());
        configMapper.updateById(config);
    }

    @Override
    public DistributionPartner getByUserId(Long userId) {
        return partnerMapper.selectByUserId(userId);
    }

    @Override
    public DistributionPartner requireByUserId(Long userId) {
        DistributionPartner partner = getByUserId(userId);
        if (partner == null) {
            throw new ServiceException("当前用户不是分销商", 403);
        }
        return partner;
    }

    @Override
    public DistributionPartner requireById(Long partnerId) {
        DistributionPartner partner = partnerMapper.selectById(partnerId);
        if (partner == null) {
            throw new ServiceException("分销商不存在");
        }
        return partner;
    }

    private String generatePartnerNo() {
        String prefix = "FX" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        for (int i = 0; i < 20; i++) {
            String candidate = prefix + randomCode(6);
            if (partnerMapper.countByPartnerNo(candidate) == 0) {
                return candidate;
            }
        }
        throw new ServiceException("分销商编号生成失败，请重试");
    }

    private String generateInviteCode() {
        for (int i = 0; i < 20; i++) {
            String candidate = randomCode(12);
            if (partnerMapper.countByInviteCode(candidate) == 0) {
                return candidate;
            }
        }
        throw new ServiceException("邀请码生成失败，请重试");
    }

    private String randomCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)]);
        }
        return builder.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
