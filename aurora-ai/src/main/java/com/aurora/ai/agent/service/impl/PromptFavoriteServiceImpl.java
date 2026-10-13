package com.aurora.ai.agent.service.impl;

import com.aurora.ai.agent.domain.PromptFavorite;
import com.aurora.ai.agent.dto.PromptFavoriteSaveRequest;
import com.aurora.ai.agent.dto.PromptFavoriteVO;
import com.aurora.ai.agent.mapper.PromptFavoriteMapper;
import com.aurora.ai.agent.service.IPromptFavoriteService;
import com.aurora.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 用户提示词收藏服务实现。
 */
@Service
public class PromptFavoriteServiceImpl extends ServiceImpl<PromptFavoriteMapper, PromptFavorite>
        implements IPromptFavoriteService {

    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_CONTENT_LENGTH = 32000;
    private static final int MAX_PAGE_SIZE = 50;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(Long userId, PromptFavoriteSaveRequest request) {
        if (userId == null) {
            throw new ServiceException("请先登录");
        }
        String name = normalize(request == null ? null : request.getName(), "请输入备注名称", MAX_NAME_LENGTH);
        String content = normalize(request == null ? null : request.getContent(), "提示词不能为空", MAX_CONTENT_LENGTH);
        String contentHash = sha256(content);

        PromptFavorite existing = baseMapper.selectAnyByUserAndHash(userId, contentHash);
        if (existing != null) {
            if (!"2".equals(existing.getDelFlag())) {
                throw new ServiceException("该提示词已收藏");
            }
            int restored = baseMapper.restore(existing.getId(), userId, name, content);
            if (restored == 0) {
                throw new ServiceException("该提示词已收藏");
            }
            return;
        }

        PromptFavorite favorite = new PromptFavorite();
        favorite.setUserId(userId);
        favorite.setName(name);
        favorite.setContent(content);
        favorite.setContentHash(contentHash);
        try {
            save(favorite);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("该提示词已收藏");
        }
    }

    @Override
    public IPage<PromptFavoriteVO> pageOwned(Long userId, int pageNum, int pageSize) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        IPage<PromptFavorite> page = page(
                new Page<>(safePageNum, safePageSize),
                new LambdaQueryWrapper<PromptFavorite>()
                        .eq(PromptFavorite::getUserId, userId)
                        .orderByDesc(PromptFavorite::getCreateTime)
                        .orderByDesc(PromptFavorite::getId));
        return page.convert(this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeOwned(Long userId, Long id) {
        if (id == null || !remove(new LambdaQueryWrapper<PromptFavorite>()
                .eq(PromptFavorite::getId, id)
                .eq(PromptFavorite::getUserId, userId))) {
            throw new ServiceException("收藏不存在");
        }
    }

    private String normalize(String value, String emptyMessage, int maxLength) {
        String original = value == null ? "" : HtmlUtils.htmlUnescape(value).trim();
        if (original.isEmpty()) {
            throw new ServiceException(emptyMessage);
        }
        if (original.codePointCount(0, original.length()) > maxLength) {
            throw new ServiceException(maxLength == MAX_NAME_LENGTH
                    ? "备注名称最多50个字符"
                    : "提示词最多32000个字符");
        }
        return original;
    }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前运行环境不支持SHA-256", e);
        }
    }

    private PromptFavoriteVO toVO(PromptFavorite favorite) {
        PromptFavoriteVO vo = new PromptFavoriteVO();
        vo.setId(favorite.getId());
        vo.setName(favorite.getName());
        vo.setContent(favorite.getContent());
        vo.setCreateTime(favorite.getCreateTime());
        return vo;
    }
}
