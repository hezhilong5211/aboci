package com.aurora.admin.controller.mobile;

import com.aurora.admin.mobile.domain.GlobalPrompt;
import com.aurora.admin.mobile.dto.GlobalPromptSaveDTO;
import com.aurora.admin.mobile.service.IGlobalPromptService;
import com.aurora.common.core.annotation.Log;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.enums.BusinessType;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全局提示词 管理后台控制器(当前生效 + 历史版本 + 保存新版本 + 启用历史版本)
 * 「全局唯一生效」由 Service 事务保证(先全表置0、再置目标为1)。
 */
@Tag(name = "全局提示词", description = "全局提示词管理(全局唯一生效 + 历史版本)")
@RestController
@RequestMapping("/system/global-prompt")
@RequiredArgsConstructor
public class GlobalPromptController {

    private final IGlobalPromptService globalPromptService;

    /**
     * 当前生效内容(无则返回 null)
     */
    @Operation(summary = "当前生效内容", description = "返回当前 is_active=1 的一条,无则 null")
    @GetMapping("/active")
    @PreAuthorize("@ss.hasAnyPermi('globalprompt:list', 'globalprompt:edit')")
    public Result<GlobalPrompt> getActive() {
        return Result.success(globalPromptService.getActive());
    }

    /**
     * 历史版本分页(create_time 倒序)
     */
    @Operation(summary = "历史版本分页", description = "create_time 倒序")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAnyPermi('globalprompt:list', 'globalprompt:edit')")
    public Result<IPage<GlobalPrompt>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(globalPromptService.pageHistory(pageNum, pageSize));
    }

    /**
     * 保存为新版本并即时生效(新插一条 is_active=1,其余置 0)
     */
    @Operation(summary = "保存并生效", description = "保存为新版本并即时生效;事务内先全表置0再插新内容置1")
    @PostMapping
    @PreAuthorize("@ss.hasPermi('globalprompt:edit')")
    @Log(title = "全局提示词", businessType = BusinessType.INSERT)
    public Result<Void> save(@RequestBody GlobalPromptSaveDTO dto) {
        if (!StringUtils.hasText(dto.getContent())) {
            return Result.error("提示词内容不能为空");
        }
        globalPromptService.saveAndActivate(dto.getContent());
        return Result.success();
    }

    /**
     * 启用某历史版本(该条置1、其余置0)
     */
    @Operation(summary = "启用历史版本", description = "启用某历史版本;事务内全表置0、目标置1")
    @PutMapping("/active/{id}")
    @PreAuthorize("@ss.hasPermi('globalprompt:edit')")
    @Log(title = "全局提示词", businessType = BusinessType.UPDATE)
    public Result<Void> activate(@PathVariable Long id) {
        globalPromptService.activate(id);
        return Result.success();
    }
}
