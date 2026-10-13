package com.aurora.admin.distribution;

import com.aurora.admin.distribution.domain.DistributionConfig;
import com.aurora.admin.distribution.domain.DistributionPartner;
import com.aurora.admin.distribution.domain.DistributionRelation;
import com.aurora.admin.distribution.domain.DistributionVisit;
import com.aurora.admin.distribution.dto.DistributionDtos;
import com.aurora.admin.distribution.mapper.DistributionPartnerMapper;
import com.aurora.admin.distribution.mapper.DistributionRelationMapper;
import com.aurora.admin.distribution.mapper.DistributionVisitMapper;
import com.aurora.admin.distribution.service.IDistributionPartnerService;
import com.aurora.admin.distribution.service.impl.DistributionAttributionServiceImpl;
import com.aurora.admin.mobile.domain.MobileUser;
import com.aurora.admin.mobile.mapper.MobileUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistributionAttributionServiceImplTest {

    @Mock
    private IDistributionPartnerService partnerService;
    @Mock
    private DistributionPartnerMapper partnerMapper;
    @Mock
    private DistributionRelationMapper relationMapper;
    @Mock
    private DistributionVisitMapper visitMapper;
    @Mock
    private MobileUserMapper mobileUserMapper;

    private DistributionAttributionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DistributionAttributionServiceImpl(
                partnerService, partnerMapper, relationMapper, visitMapper, mobileUserMapper);
    }

    @Test
    void bindsFirstValidPartnerAndKeepsVisitSnapshot() {
        LocalDateTime visitTime = LocalDateTime.now().minusMinutes(2);
        when(relationMapper.selectByReferredUserId(22L)).thenReturn(null);
        when(partnerService.getConfig()).thenReturn(enabledConfig());
        DistributionPartner partner = partner(7L, 11L);
        when(partnerMapper.selectByInviteCode("ABC234EFG567")).thenReturn(partner);
        DistributionVisit visit = new DistributionVisit();
        visit.setVisitId(31L);
        visit.setEventId("12345678-1234-1234-1234-123456789012");
        visit.setPartnerId(7L);
        visit.setVisitTime(visitTime);
        when(visitMapper.selectByEventIdForUpdate(visit.getEventId())).thenReturn(visit);
        MobileUser user = new MobileUser();
        user.setUserId(22L);
        user.setStatus("0");
        user.setCreateTime(visitTime.minusDays(10));
        when(mobileUserMapper.selectById(22L)).thenReturn(user);

        DistributionDtos.Bind request = new DistributionDtos.Bind();
        request.setInviteCode("ABC234EFG567");
        request.setEventId(visit.getEventId());
        var result = service.bind(22L, request);

        assertTrue(result.getBound());
        assertFalse(result.getAlreadyBound());
        ArgumentCaptor<DistributionRelation> relationCaptor = ArgumentCaptor.forClass(DistributionRelation.class);
        verify(relationMapper).insert(relationCaptor.capture());
        assertEquals(7L, relationCaptor.getValue().getPartnerId());
        assertEquals(22L, relationCaptor.getValue().getReferredUserId());
        assertEquals(1, relationCaptor.getValue().getExistingUser());
        assertEquals(visitTime, relationCaptor.getValue().getFirstVisitTime());
        assertEquals(1, visit.getBound());
        assertEquals(22L, visit.getUserId());
        verify(visitMapper).updateById(visit);
    }

    @Test
    void existingRelationIsNeverOverwritten() {
        DistributionRelation relation = new DistributionRelation();
        relation.setPartnerId(8L);
        when(relationMapper.selectByReferredUserId(22L)).thenReturn(relation);
        DistributionPartner original = partner(8L, 33L);
        original.setPartnerNo("FX-OLD");
        when(partnerMapper.selectById(8L)).thenReturn(original);

        DistributionDtos.Bind request = new DistributionDtos.Bind();
        request.setInviteCode("NEW-CODE");
        request.setEventId("12345678-1234-1234-1234-123456789012");
        var result = service.bind(22L, request);

        assertTrue(result.getAlreadyBound());
        assertEquals("FX-OLD", result.getPartnerNo());
        verify(relationMapper, never()).insert(
                (DistributionRelation) org.mockito.ArgumentMatchers.any(DistributionRelation.class));
        verify(partnerService, never()).getConfig();
    }

    @Test
    void currentPartnerScanningOwnCodeIsSuccessfulNoOp() {
        when(relationMapper.selectByReferredUserId(11L)).thenReturn(null);
        when(partnerService.getConfig()).thenReturn(enabledConfig());
        when(partnerMapper.selectByInviteCode("ABC234EFG567")).thenReturn(partner(7L, 11L));
        DistributionDtos.Bind request = new DistributionDtos.Bind();
        request.setInviteCode("ABC234EFG567");
        request.setEventId("12345678-1234-1234-1234-123456789012");

        var result = service.bind(11L, request);

        assertTrue(result.getBound());
        assertTrue(result.getAlreadyBound());
        assertEquals("FX1001", result.getPartnerNo());
        verify(relationMapper, never()).insert(any(DistributionRelation.class));
        verify(visitMapper, never()).selectByEventIdForUpdate(anyString());
        verify(visitMapper, never()).updateById(any(DistributionVisit.class));
    }

    private DistributionConfig enabledConfig() {
        DistributionConfig config = new DistributionConfig();
        config.setEnabled(1);
        return config;
    }

    private DistributionPartner partner(Long partnerId, Long userId) {
        DistributionPartner partner = new DistributionPartner();
        partner.setPartnerId(partnerId);
        partner.setUserId(userId);
        partner.setPartnerNo("FX1001");
        partner.setInviteCode("ABC234EFG567");
        partner.setStatus("ACTIVE");
        partner.setEnabledTime(LocalDateTime.now().minusDays(1));
        return partner;
    }
}
