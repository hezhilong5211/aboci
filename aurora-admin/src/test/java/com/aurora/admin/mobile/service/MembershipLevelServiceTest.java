package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.MembershipLevel;
import com.aurora.admin.mobile.mapper.BeanRechargePackageMapper;
import com.aurora.admin.mobile.mapper.MembershipLevelMapper;
import com.aurora.admin.mobile.service.impl.MembershipLevelServiceImpl;
import com.aurora.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MembershipLevelServiceTest {

    private MembershipLevelMapper membershipLevelMapper;
    private MembershipLevelServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        membershipLevelMapper = mock(MembershipLevelMapper.class);
        service = new MembershipLevelServiceImpl(
                mock(BeanRechargePackageMapper.class),
                mock(ISysWxConfigService.class));
        Field field = findField(service.getClass(), "baseMapper");
        field.setAccessible(true);
        field.set(service, membershipLevelMapper);
    }

    @Test
    void 保存会员等级时保留实际配置天数() {
        MembershipLevel level = level(90);
        ArgumentCaptor<MembershipLevel> captor =
                ArgumentCaptor.forClass(MembershipLevel.class);
        when(membershipLevelMapper.insert(captor.capture())).thenReturn(1);

        service.save(level);

        assertEquals(90, captor.getValue().getDurationDays());
    }

    @Test
    void 会员时长超出范围时拒绝保存() {
        ServiceException tooShort = assertThrows(
                ServiceException.class, () -> service.save(level(0)));
        ServiceException tooLong = assertThrows(
                ServiceException.class, () -> service.save(level(3651)));

        assertEquals("会员时长必须在1到3650天之间", tooShort.getMessage());
        assertEquals("会员时长必须在1到3650天之间", tooLong.getMessage());
        verify(membershipLevelMapper, never()).insert(
                org.mockito.ArgumentMatchers.<MembershipLevel>any());
    }

    private MembershipLevel level(int durationDays) {
        MembershipLevel level = new MembershipLevel();
        level.setName("测试会员");
        level.setGrantBeans(1000L);
        level.setPrice(new BigDecimal("99.00"));
        level.setDurationDays(durationDays);
        level.setSort(1);
        level.setStatus("1");
        return level;
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
