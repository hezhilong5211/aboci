package com.aurora.ai.agent.controller;

import com.aurora.ai.agent.dto.PromptFavoriteSaveRequest;
import com.aurora.ai.agent.dto.PromptFavoriteVO;
import com.aurora.ai.agent.service.IPromptFavoriteService;
import com.aurora.common.core.domain.Result;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 移动端提示词收藏接口。
 */
@RestController
@RequestMapping("/api/mobile/agent/prompt-favorites")
@RequiredArgsConstructor
public class PromptFavoriteController {

    private final IPromptFavoriteService promptFavoriteService;

    @PostMapping
    public Result<Void> create(@RequestBody PromptFavoriteSaveRequest request,
                               @RequestAttribute("mobileUserId") Long userId) {
        promptFavoriteService.create(userId, request);
        return Result.success();
    }

    @GetMapping
    public Result<IPage<PromptFavoriteVO>> page(
            @RequestAttribute("mobileUserId") Long userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(promptFavoriteService.pageOwned(userId, pageNum, pageSize));
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id,
                               @RequestAttribute("mobileUserId") Long userId) {
        promptFavoriteService.removeOwned(userId, id);
        return Result.success();
    }
}
