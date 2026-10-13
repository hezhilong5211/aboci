package com.aurora.admin.mobile.controller;

import com.aurora.admin.mobile.domain.BeanTransaction;
import com.aurora.admin.mobile.dto.BeanRechargeDTO;
import com.aurora.admin.mobile.service.IBeanAccountService;
import com.aurora.admin.mobile.service.IBeanRechargePackageService;
import com.aurora.admin.mobile.service.IBeanTransactionService;
import com.aurora.admin.mobile.service.IMembershipExpiryService;
import com.aurora.admin.mobile.service.IUserMembershipService;
import com.aurora.admin.mobile.vo.BeanAccountVO;
import com.aurora.admin.mobile.vo.BeanRechargePackageVO;
import com.aurora.admin.mobile.vo.CreateOrderVO;
import com.aurora.common.core.domain.Result;
import com.aurora.framework.mobile.MobileJwtFilter;
import com.baomidou.mybatisplus.core.metadata.IPage;
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

import java.util.List;

/**
 * 移动端豆子控制器:当前用户余额与流水(只读,需鉴权)
 * userId 由 MobileJwtFilter 注入的请求属性取;/api/mobile/bean/** 未列入 permitAll,默认 authenticated。
 */
@Tag(name = "移动端豆子", description = "当前用户豆子余额与流水查询")
@RestController
@RequestMapping("/api/mobile/bean")
@RequiredArgsConstructor
public class MobileBeanController {

    private final IBeanAccountService beanAccountService;
    private final IBeanTransactionService beanTransactionService;
    private final IBeanRechargePackageService beanRechargePackageService;
    private final IMembershipExpiryService membershipExpiryService;
    private final IUserMembershipService userMembershipService;

    /**
     * 当前用户余额(无账户视为全 0)
     */
    @Operation(summary = "查询余额", description = "返回当前用户 balance/totalEarned/totalConsumed")
    @GetMapping("/balance")
    public Result<BeanAccountVO> balance(
            @RequestAttribute(MobileJwtFilter.MOBILE_USER_ID_ATTR) Long userId) {
        membershipExpiryService.settleUserIfExpired(userId);
        return Result.success(beanAccountService.getAccount(userId));
    }

    /**
     * 当前用户流水分页(按时间倒序)
     */
    @Operation(summary = "查询流水", description = "分页查询当前用户豆子流水,按时间倒序")
    @GetMapping("/transactions")
    public Result<IPage<BeanTransaction>> transactions(
            @RequestAttribute(MobileJwtFilter.MOBILE_USER_ID_ATTR) Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        membershipExpiryService.settleUserIfExpired(userId);
        return Result.success(beanTransactionService.pageByUser(userId, pageNum, pageSize));
    }

    /**
     * 上架充值套餐列表(status=0,按 sort 升序)
     */
    @Operation(summary = "充值套餐列表", description = "返回上架豆子充值套餐,按排序升序")
    @GetMapping("/package/list")
    public Result<List<BeanRechargePackageVO>> packageList(
            @RequestAttribute(MobileJwtFilter.MOBILE_USER_ID_ATTR) Long userId) {
        userMembershipService.requireActiveMembership(userId);
        return Result.success(beanRechargePackageService.listOnShelf().stream()
                .map(BeanRechargePackageVO::from)
                .toList());
    }

    /**
     * 豆子充值下单。入参仅 packageId,金额与到账豆数一律服务端按套餐计算。
     */
    @Operation(summary = "豆子充值下单", description = "按套餐ID下单,服务端计算金额并发起微信支付")
    @PostMapping("/package/recharge")
    public Result<CreateOrderVO> recharge(
            @RequestAttribute(MobileJwtFilter.MOBILE_USER_ID_ATTR) Long userId,
            @RequestBody BeanRechargeDTO dto) {
        return Result.success(beanRechargePackageService.createRecharge(userId, dto.getPackageId()));
    }
}
