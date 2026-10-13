package com.aurora.admin.mobile.controller;

import com.aurora.admin.mobile.dto.VirtualPayCreateDTO;
import com.aurora.admin.mobile.dto.VirtualPayClientFailureDTO;
import com.aurora.admin.mobile.service.IVirtualPayService;
import com.aurora.admin.mobile.vo.OrderVO;
import com.aurora.admin.mobile.vo.VirtualPayCreateVO;
import com.aurora.common.core.domain.Result;
import com.aurora.framework.mobile.MobileJwtFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 移动端微信虚拟支付（普通道具直购）接口，均需移动端登录。 */
@Tag(name = "移动端虚拟支付", description = "道具直购下单与查单补发")
@RestController
@RequestMapping("/api/mobile/vpay")
@RequiredArgsConstructor
public class VirtualPayController {

    private final IVirtualPayService virtualPayService;

    @Operation(summary = "创建虚拟支付订单", description = "价格、道具ID和权益均由服务端按本地商品读取")
    @PostMapping("/create")
    public Result<VirtualPayCreateVO> create(
            @RequestAttribute(MobileJwtFilter.MOBILE_USER_ID_ATTR) Long userId,
            @RequestBody VirtualPayCreateDTO dto) {
        return Result.success(virtualPayService.create(userId, dto));
    }

    @Operation(summary = "查询虚拟支付订单", description = "syncWechat=false 仅查本地状态；默认同步微信并必要时补发")
    @GetMapping("/query")
    public Result<OrderVO> query(
            @RequestAttribute(MobileJwtFilter.MOBILE_USER_ID_ATTR) Long userId,
            @RequestParam String orderNo,
            @RequestParam(defaultValue = "true") boolean syncWechat) {
        return Result.success(virtualPayService.query(userId, orderNo, syncWechat));
    }

    @Operation(summary = "上报虚拟支付客户端失败", description = "仅记录微信原始错误码供排障，不改变订单状态")
    @PostMapping("/client-failure")
    public Result<Void> reportClientFailure(
            @RequestAttribute(MobileJwtFilter.MOBILE_USER_ID_ATTR) Long userId,
            @RequestBody VirtualPayClientFailureDTO dto) {
        virtualPayService.reportClientFailure(userId, dto);
        return Result.success();
    }
}
