package com.aurora.admin.controller.mobile;

import com.aurora.admin.mobile.domain.BeanTransaction;
import com.aurora.admin.mobile.dto.BeanAdjustDTO;
import com.aurora.admin.mobile.service.IBeanAccountService;
import com.aurora.admin.mobile.service.IBeanTransactionService;
import com.aurora.common.core.annotation.Log;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.enums.BusinessType;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 豆子账户 管理后台控制器
 * 仅手动调整 + 流水查看;余额变动统一经 IBeanAccountService(唯一写入路径)。
 */
@Tag(name = "豆子账户", description = "后台手动增减豆子与流水查看")
@RestController
@RequestMapping("/system/mobile/bean")
@RequiredArgsConstructor
public class BeanAccountAdminController {

    private final IBeanAccountService beanAccountService;
    private final IBeanTransactionService beanTransactionService;

    /**
     * 手动增减豆子(amount 正加负减,≠0),记 ADJUST 流水
     */
    @Operation(summary = "手动增减豆子", description = "amount 正加负减,不能为 0;减豆走原子扣减,余额不足返回错误")
    @PostMapping("/adjust")
    @PreAuthorize("@ss.hasPermi('mobile:bean:adjust')")
    @Log(title = "豆子账户", businessType = BusinessType.UPDATE)
    public Result<Void> adjust(@RequestBody BeanAdjustDTO dto) {
        if (dto.getUserId() == null) {
            return Result.error("userId 不能为空");
        }
        if (dto.getAmount() == null || dto.getAmount() == 0) {
            return Result.error("调整数量不能为 0");
        }
        beanAccountService.adjust(dto.getUserId(), dto.getAmount(), dto.getRemark());
        return Result.success();
    }

    /**
     * 分页查询某用户流水
     */
    @Operation(summary = "查询用户流水", description = "按 userId 分页查询豆子流水,按时间倒序")
    @GetMapping("/transactions")
    @PreAuthorize("@ss.hasPermi('mobile:bean:list')")
    public Result<IPage<BeanTransaction>> transactions(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(beanTransactionService.pageByUser(userId, pageNum, pageSize));
    }
}
