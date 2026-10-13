package com.aurora.admin.distribution.service.impl;

import com.aurora.admin.distribution.domain.DistributionPartner;
import com.aurora.admin.distribution.service.IDistributionPartnerService;
import com.aurora.admin.distribution.service.IDistributionQrcodeService;
import com.aurora.admin.distribution.service.IDistributionQueryService;
import com.aurora.admin.distribution.storage.DistributionPrivateStorage;
import com.aurora.admin.distribution.support.DistributionConstants;
import com.aurora.admin.distribution.vo.DistributionViews;
import com.aurora.admin.mobile.service.WxServiceFactory;
import com.aurora.common.core.exception.ServiceException;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class DistributionQrcodeServiceImpl implements IDistributionQrcodeService {

    private static final Set<String> SUPPORTED_ENV_VERSIONS = Set.of("release", "trial", "develop");

    private final IDistributionPartnerService partnerService;
    private final IDistributionQueryService queryService;
    private final DistributionPrivateStorage privateStorage;
    private final WxServiceFactory wxServiceFactory;
    private final String qrcodeEnvVersion;

    public DistributionQrcodeServiceImpl(
            IDistributionPartnerService partnerService,
            IDistributionQueryService queryService,
            DistributionPrivateStorage privateStorage,
            WxServiceFactory wxServiceFactory,
            @Value("${distribution.qrcode-env-version:release}") String qrcodeEnvVersion) {
        this.partnerService = partnerService;
        this.queryService = queryService;
        this.privateStorage = privateStorage;
        this.wxServiceFactory = wxServiceFactory;
        this.qrcodeEnvVersion = normalizeEnvVersion(qrcodeEnvVersion);
    }

    @Override
    public DistributionViews.Qrcode getQrcode(Long partnerId) {
        DistributionPartner partner = partnerService.requireById(partnerId);
        DistributionViews.Dashboard dashboard = queryService.dashboard(partnerId);

        DistributionViews.Qrcode result = new DistributionViews.Qrcode();
        result.setStatus(partner.getStatus());
        result.setVisitCount(dashboard.getVisitCount());
        result.setPromotedUserCount(dashboard.getPromotedUserCount());

        String filename = partner.getInviteCode() + "_" + qrcodeEnvVersion + ".png";
        String fileKey = "qrcode/" + partnerId + "/" + filename;
        if (!privateStorage.exists(fileKey) && DistributionConstants.PARTNER_ACTIVE.equals(partner.getStatus())) {
            generate(partner, fileKey, filename);
        }
        if (privateStorage.exists(fileKey)) {
            result.setImageData(privateStorage.readDataUri(fileKey));
        }
        return result;
    }

    private void generate(DistributionPartner partner, String expectedFileKey, String filename) {
        try {
            byte[] bytes = wxServiceFactory.getWxMaService().getQrcodeService().createWxaCodeUnlimitBytes(
                    "d=" + partner.getInviteCode(),
                    "pages/index/index",
                    "release".equals(qrcodeEnvVersion),
                    qrcodeEnvVersion,
                    430,
                    true,
                    null,
                    false);
            String actualKey = privateStorage.savePng("qrcode", partner.getPartnerId(),
                    filename, bytes);
            if (!expectedFileKey.equals(actualKey)) {
                throw new ServiceException("推广二维码缓存失败");
            }
        } catch (WxErrorException e) {
            throw new ServiceException("微信推广二维码生成失败，请检查小程序配置", e);
        }
    }

    private static String normalizeEnvVersion(String envVersion) {
        String normalized = envVersion == null ? "" : envVersion.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_ENV_VERSIONS.contains(normalized)) {
            throw new ServiceException("分销二维码版本配置错误，仅支持 release、trial 或 develop");
        }
        return normalized;
    }
}
