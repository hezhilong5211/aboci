package com.aurora.admin.distribution;

import cn.binarywang.wx.miniapp.api.WxMaQrcodeService;
import cn.binarywang.wx.miniapp.api.WxMaService;
import com.aurora.admin.distribution.domain.DistributionPartner;
import com.aurora.admin.distribution.service.IDistributionPartnerService;
import com.aurora.admin.distribution.service.IDistributionQueryService;
import com.aurora.admin.distribution.service.impl.DistributionQrcodeServiceImpl;
import com.aurora.admin.distribution.storage.DistributionPrivateStorage;
import com.aurora.admin.distribution.vo.DistributionViews;
import com.aurora.admin.mobile.service.WxServiceFactory;
import com.aurora.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistributionQrcodeServiceImplTest {

    @Mock private IDistributionPartnerService partnerService;
    @Mock private IDistributionQueryService queryService;
    @Mock private DistributionPrivateStorage privateStorage;
    @Mock private WxServiceFactory wxServiceFactory;
    @Mock private WxMaService wxMaService;
    @Mock private WxMaQrcodeService wxMaQrcodeService;

    private DistributionPartner partner;
    private DistributionViews.Dashboard dashboard;

    @BeforeEach
    void setUp() {
        partner = new DistributionPartner();
        partner.setPartnerId(7L);
        partner.setInviteCode("INVITE01");
        partner.setStatus("ACTIVE");

        dashboard = new DistributionViews.Dashboard();
        dashboard.setVisitCount(3L);
        dashboard.setPromotedUserCount(2L);
    }

    @Test
    void generatesExperienceQrcodeWithVersionedCacheKey() throws Exception {
        DistributionQrcodeServiceImpl service = new DistributionQrcodeServiceImpl(
                partnerService, queryService, privateStorage, wxServiceFactory, "trial");
        String fileKey = "qrcode/7/INVITE01_trial.png";
        byte[] image = new byte[] {1, 2, 3};
        when(partnerService.requireById(7L)).thenReturn(partner);
        when(queryService.dashboard(7L)).thenReturn(dashboard);
        when(privateStorage.exists(fileKey)).thenReturn(false, true);
        when(wxServiceFactory.getWxMaService()).thenReturn(wxMaService);
        when(wxMaService.getQrcodeService()).thenReturn(wxMaQrcodeService);
        when(wxMaQrcodeService.createWxaCodeUnlimitBytes(
                eq("d=INVITE01"), eq("pages/index/index"), eq(false), eq("trial"),
                eq(430), eq(true), isNull(), eq(false))).thenReturn(image);
        when(privateStorage.savePng("qrcode", 7L, "INVITE01_trial.png", image)).thenReturn(fileKey);
        when(privateStorage.readDataUri(fileKey)).thenReturn("data:image/png;base64,AQID");

        DistributionViews.Qrcode result = service.getQrcode(7L);

        assertEquals("data:image/png;base64,AQID", result.getImageData());
        assertEquals(3L, result.getVisitCount());
        assertEquals(2L, result.getPromotedUserCount());
        verify(wxMaQrcodeService).createWxaCodeUnlimitBytes(
                eq("d=INVITE01"), eq("pages/index/index"), eq(false), eq("trial"),
                eq(430), eq(true), isNull(), eq(false));
    }

    @Test
    void keepsPublishedPageValidationForReleaseQrcode() throws Exception {
        DistributionQrcodeServiceImpl service = new DistributionQrcodeServiceImpl(
                partnerService, queryService, privateStorage, wxServiceFactory, "release");
        String fileKey = "qrcode/7/INVITE01_release.png";
        byte[] image = new byte[] {1, 2, 3};
        when(partnerService.requireById(7L)).thenReturn(partner);
        when(queryService.dashboard(7L)).thenReturn(dashboard);
        when(privateStorage.exists(fileKey)).thenReturn(false, true);
        when(wxServiceFactory.getWxMaService()).thenReturn(wxMaService);
        when(wxMaService.getQrcodeService()).thenReturn(wxMaQrcodeService);
        when(wxMaQrcodeService.createWxaCodeUnlimitBytes(
                eq("d=INVITE01"), eq("pages/index/index"), eq(true), eq("release"),
                eq(430), eq(true), isNull(), eq(false))).thenReturn(image);
        when(privateStorage.savePng("qrcode", 7L, "INVITE01_release.png", image)).thenReturn(fileKey);
        when(privateStorage.readDataUri(fileKey)).thenReturn("data:image/png;base64,AQID");

        service.getQrcode(7L);

        verify(wxMaQrcodeService).createWxaCodeUnlimitBytes(
                eq("d=INVITE01"), eq("pages/index/index"), eq(true), eq("release"),
                eq(430), eq(true), isNull(), eq(false));
    }

    @Test
    void rejectsUnsupportedQrcodeEnvironment() {
        assertThrows(ServiceException.class, () -> new DistributionQrcodeServiceImpl(
                partnerService, queryService, privateStorage, wxServiceFactory, "preview"));
    }
}
