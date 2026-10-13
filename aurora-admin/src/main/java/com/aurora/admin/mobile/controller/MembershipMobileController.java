package com.aurora.admin.mobile.controller;

import com.aurora.admin.mobile.domain.MembershipLevel;
import com.aurora.admin.mobile.dto.PurchaseMembershipDTO;
import com.aurora.admin.mobile.service.IMembershipLevelService;
import com.aurora.admin.mobile.service.IPayOrderBizService;
import com.aurora.admin.mobile.vo.CreateOrderVO;
import com.aurora.admin.mobile.vo.MembershipLevelVO;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.framework.mobile.MobileJwtFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 移动端会员控制器:可购买等级列表 + 会员购买下单(使用类接口,需鉴权)
 * userId 由 MobileJwtFilter 注入的请求属性取;价格与时长服务端按等级查取,绝不信任前端金额。
 */
@Tag(name = "移动端会员", description = "C 端可购买会员等级列表与购买下单")
@RestController
@RequestMapping("/api/mobile/membership")
@RequiredArgsConstructor
public class MembershipMobileController {

    private final IMembershipLevelService membershipLevelService;
    private final IPayOrderBizService payOrderBizService;

    /**
     * 可购买会员等级列表(status=0 上架,按 sort 升序)
     */
    @Operation(summary = "可购买等级列表", description = "返回上架会员等级,含价格(元)/时长/送豆数/卡片背景")
    @GetMapping("/levels")
    public Result<List<MembershipLevelVO>> levels() {
        List<MembershipLevelVO> list = membershipLevelService.listOnSale().stream()
                .map(MembershipLevelVO::from)
                .toList();
        return Result.success(list);
    }

    /**
     * 会员购买下单:按 levelId 查等级取价格(元)→×100 转分→创建 MEMBERSHIP 支付订单。
     * 金额与商品名快照均服务端按等级生成,前端仅传 levelId。
     */
    @Operation(summary = "会员购买下单", description = "入参仅 levelId;服务端查等级算金额,返回调起支付参数")
    @PostMapping("/purchase")
    public Result<CreateOrderVO> purchase(@RequestBody PurchaseMembershipDTO dto,
                                          @RequestAttribute(MobileJwtFilter.MOBILE_USER_ID_ATTR) Long userId) {
        if (dto.getLevelId() == null) {
            throw new ServiceException("levelId 不能为空");
        }
        MembershipLevel level = membershipLevelService.getById(dto.getLevelId());
        if (level == null || !"0".equals(level.getStatus())) {
            throw new ServiceException("会员等级不存在或已下架");
        }
        if (level.getPrice() == null || level.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("会员等级价格未配置");
        }
        if (level.getVirtualProductId() != null && !level.getVirtualProductId().isBlank()) {
            throw new ServiceException("该会员商品仅支持微信虚拟支付，请更新或重新打开小程序");
        }
        // 价格(元)×100 转分,四舍五入后取整,作为支付金额
        int amountFen = level.getPrice().movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).intValueExact();
        CreateOrderVO vo = payOrderBizService.createOrder(
                userId, amountFen, "会员:" + level.getName(),
                "MEMBERSHIP", level.getLevelId(), level.getName());
        return Result.success(vo);
    }
}
