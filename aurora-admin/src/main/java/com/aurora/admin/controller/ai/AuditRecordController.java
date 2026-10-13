package com.aurora.admin.controller.ai;

import com.aurora.ai.agent.domain.AuditRecord;
import com.aurora.ai.agent.service.IAuditRecordService;
import com.aurora.common.core.domain.Result;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内容审核日志控制器。
 *
 * 只接请求/包 {@link Result},分页查询业务在 {@link IAuditRecordService#pageQuery}(R-LAYER)。
 *
 * @author aurora
 */
@Tag(name = "内容审核日志", description = "内容审核存证流水查询接口")
@RestController
@RequestMapping("/ai/audit")
@RequiredArgsConstructor
public class AuditRecordController {

    private final IAuditRecordService auditRecordService;

    /**
     * 分页查询审核记录(按 create_time 倒序),各筛选条件均可空。
     */
    @Operation(summary = "分页查询审核记录", description = "按对象类型/建议/处置/用户/时间区间分页查询内容审核存证流水")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('ai:audit:list')")
    public Result<IPage<AuditRecord>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String suggest,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime) {
        return Result.success(auditRecordService.pageQuery(
                pageNum, pageSize, objectType, suggest, result, userId, beginTime, endTime));
    }
}
