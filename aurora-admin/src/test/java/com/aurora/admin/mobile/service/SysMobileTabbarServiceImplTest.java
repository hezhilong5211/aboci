package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.SysMobileTabbar;
import com.aurora.admin.mobile.mapper.SysMobileTabbarMapper;
import com.aurora.admin.mobile.service.impl.SysMobileTabbarServiceImpl;
import com.aurora.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SysMobileTabbarServiceImplTest {

    private SysMobileTabbarServiceImpl service;
    private SysMobileTabbarMapper mapper;

    @BeforeEach
    void setUp() {
        service = new SysMobileTabbarServiceImpl();
        mapper = mock(SysMobileTabbarMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void 禁止关闭最后一个启用项() {
        SysMobileTabbar chat = tab(1L, 1);
        SysMobileTabbar create = tab(2L, 0);
        when(mapper.selectAllForUpdate()).thenReturn(List.of(chat, create));
        SysMobileTabbar update = tab(1L, 0);

        assertThatThrownBy(() -> service.editEditable(update))
                .isInstanceOf(ServiceException.class)
                .hasMessage("底部导航至少保留一项启用");
    }

    @Test
    void 拒绝非法启用状态() {
        assertThatThrownBy(() -> service.editEditable(tab(1L, 2)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("启用状态只能为 0 或 1");
    }

    private SysMobileTabbar tab(Long id, int visible) {
        SysMobileTabbar tab = new SysMobileTabbar();
        tab.setTabId(id);
        tab.setVisible(visible);
        return tab;
    }
}
