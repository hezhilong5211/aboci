package com.aurora.ai.agent.service;

import com.aurora.ai.agent.domain.PromptFavorite;
import com.aurora.ai.agent.dto.PromptFavoriteSaveRequest;
import com.aurora.ai.agent.dto.PromptFavoriteVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户提示词收藏服务。
 */
public interface IPromptFavoriteService extends IService<PromptFavorite> {

    void create(Long userId, PromptFavoriteSaveRequest request);

    IPage<PromptFavoriteVO> pageOwned(Long userId, int pageNum, int pageSize);

    void removeOwned(Long userId, Long id);
}
