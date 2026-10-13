package com.aurora.ai.agent.service;

import com.aurora.ai.agent.domain.PromptFavorite;
import com.aurora.ai.agent.dto.PromptFavoriteSaveRequest;
import com.aurora.ai.agent.dto.PromptFavoriteVO;
import com.aurora.ai.agent.mapper.PromptFavoriteMapper;
import com.aurora.ai.agent.service.impl.PromptFavoriteServiceImpl;
import com.aurora.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptFavoriteServiceTest {

    @Mock
    private PromptFavoriteMapper mapper;

    private PromptFavoriteServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PromptFavoriteServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void createsNormalizedFavorite() {
        when(mapper.selectAnyByUserAndHash(eq(7L), any())).thenReturn(null);
        when(mapper.insert(any(PromptFavorite.class))).thenReturn(1);

        service.create(7L, request("  常用封面  ", "  生成一张海报  "));

        ArgumentCaptor<PromptFavorite> captor = ArgumentCaptor.forClass(PromptFavorite.class);
        verify(mapper).insert(captor.capture());
        PromptFavorite favorite = captor.getValue();
        assertThat(favorite.getUserId()).isEqualTo(7L);
        assertThat(favorite.getName()).isEqualTo("常用封面");
        assertThat(favorite.getContent()).isEqualTo("生成一张海报");
        assertThat(favorite.getContentHash()).hasSize(64);
    }

    @Test
    void rejectsDuplicateActiveFavorite() {
        PromptFavorite existing = new PromptFavorite();
        existing.setId(11L);
        existing.setDelFlag("0");
        when(mapper.selectAnyByUserAndHash(eq(7L), any())).thenReturn(existing);

        assertThatThrownBy(() -> service.create(7L, request("备注", "相同提示词")))
                .isInstanceOf(ServiceException.class)
                .hasMessage("该提示词已收藏");
    }

    @Test
    void restoresDeletedFavorite() {
        PromptFavorite existing = new PromptFavorite();
        existing.setId(11L);
        existing.setDelFlag("2");
        when(mapper.selectAnyByUserAndHash(eq(7L), any())).thenReturn(existing);
        when(mapper.restore(11L, 7L, "新备注", "相同提示词")).thenReturn(1);

        service.create(7L, request("新备注", "相同提示词"));

        verify(mapper).restore(11L, 7L, "新备注", "相同提示词");
    }

    @Test
    void validatesUnicodeLengths() {
        assertThatThrownBy(() -> service.create(7L, request("名".repeat(51), "提示词")))
                .isInstanceOf(ServiceException.class)
                .hasMessage("备注名称最多50个字符");
        assertThatThrownBy(() -> service.create(7L, request("备注", "词".repeat(32001))))
                .isInstanceOf(ServiceException.class)
                .hasMessage("提示词最多32000个字符");
    }

    @Test
    @SuppressWarnings("unchecked")
    void pagesOnlyCurrentUsersFavorites() {
        PromptFavorite favorite = new PromptFavorite();
        favorite.setId(12L);
        favorite.setUserId(7L);
        favorite.setName("封面");
        favorite.setContent("生成封面");
        favorite.setCreateTime(LocalDateTime.of(2026, 7, 23, 10, 0));
        Page<PromptFavorite> entityPage = new Page<>(1, 10, 1);
        entityPage.setRecords(java.util.List.of(favorite));
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(entityPage);

        IPage<PromptFavoriteVO> result = service.pageOwned(7L, 1, 10);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getId()).isEqualTo(12L);
                    assertThat(item.getName()).isEqualTo("封面");
                    assertThat(item.getContent()).isEqualTo("生成封面");
                });
        verify(mapper).selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteIsScopedToCurrentUser() {
        when(mapper.delete(any(Wrapper.class))).thenReturn(1);

        service.removeOwned(7L, 12L);

        verify(mapper).delete(any(Wrapper.class));
    }

    private PromptFavoriteSaveRequest request(String name, String content) {
        PromptFavoriteSaveRequest request = new PromptFavoriteSaveRequest();
        request.setName(name);
        request.setContent(content);
        return request;
    }
}
