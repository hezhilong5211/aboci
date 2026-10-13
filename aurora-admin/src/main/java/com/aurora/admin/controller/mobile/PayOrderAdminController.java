package com.aurora.admin.controller.mobile;

import com.aurora.admin.mobile.domain.PayOrder;
import com.aurora.admin.mobile.service.IPayOrderService;
import com.aurora.common.core.domain.Result;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 支付订单 管理后台控制器(只读:列表 + 详情)
 * 订单为业务流水,不提供后台增删改;查询逻辑封装在 IPayOrderService,Controller 不写 Wrapper。
 */
@Tag(name = "订单管理", description = "支付订单查询(列表/详情)")
@RestController
@RequestMapping("/system/pay-order")
@RequiredArgsConstructor
public class PayOrderAdminController {

    private final IPayOrderService payOrderService;

    /**
     * 订单分页列表(按创建时间倒序)
     */
    @Operation(summary = "订单列表", description = "按创建时间倒序;可按状态/类型/关键词(订单号或用户ID)/时间区间筛选")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('mobile:order:list')")
    public Result<IPage<PayOrder>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime beginTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(payOrderService.pageOrders(
                status, productType, keyword, beginTime, endTime, pageNum, pageSize));
    }

    /**
     * 订单详情
     */
    @Operation(summary = "订单详情", description = "按主键ID查询订单详情")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('mobile:order:query')")
    public Result<PayOrder> detail(@PathVariable Long id) {
        return Result.success(payOrderService.getById(id));
    }
}
