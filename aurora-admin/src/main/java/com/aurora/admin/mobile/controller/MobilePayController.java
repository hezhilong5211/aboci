package com.aurora.admin.mobile.controller;

import com.aurora.admin.mobile.dto.CreateOrderDTO;
import com.aurora.admin.mobile.service.IPayOrderBizService;
import com.aurora.admin.mobile.vo.CreateOrderVO;
import com.aurora.admin.mobile.vo.OrderVO;
import com.aurora.admin.mobile.vo.PurchaseRecordVO;
import com.aurora.common.core.domain.Result;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 移动端支付接口(需鉴权)
 * 当前用户ID 从 MobileJwtFilter 注入的 mobileUserId 请求属性取。
 */
@RestController
@RequestMapping("/api/mobile/pay")
@RequiredArgsConstructor
public class MobilePayController {

    private final IPayOrderBizService payOrderBizService;

    /**
     * 创建支付订单。入参仅 amount(分)+ subject,openid 服务端取。
     */
    @PostMapping("/create")
    public Result<CreateOrderVO> create(@RequestBody CreateOrderDTO dto,
                                        @RequestAttribute("mobileUserId") Long userId) {
        return Result.success(payOrderBizService.createOrder(userId, dto.getAmount(), dto.getSubject()));
    }

    /**
     * 查询订单状态。syncWechat=true 时主动同步微信(兜底补单)。
     */
    @GetMapping("/query")
    public Result<OrderVO> query(@RequestParam String orderNo,
                                 @RequestParam(defaultValue = "false") boolean syncWechat,
                                 @RequestAttribute("mobileUserId") Long userId) {
        return Result.success(payOrderBizService.queryOrder(orderNo, syncWechat, userId));
    }

    /**
     * 我的购买记录(设置页入口):仅本人订单只读分页,按下单时间倒序。
     * 虚拟商品不支持退款,无任何操作入口。
     */
    @GetMapping("/orders")
    public Result<IPage<PurchaseRecordVO>> orders(@RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "20") int pageSize,
                                                  @RequestAttribute("mobileUserId") Long userId) {
        return Result.success(payOrderBizService.listMyOrders(userId, pageNum, pageSize));
    }
}
