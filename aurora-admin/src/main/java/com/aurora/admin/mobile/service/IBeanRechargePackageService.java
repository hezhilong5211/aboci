package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.BeanRechargePackage;
import com.aurora.admin.mobile.vo.CreateOrderVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 豆子充值套餐服务
 */
public interface IBeanRechargePackageService extends IService<BeanRechargePackage> {

    /**
     * 查询上架套餐(status=0,按 sort 升序),供 C 端充值页展示
     *
     * @return 上架套餐列表
     */
    List<BeanRechargePackage> listOnShelf();

    /**
     * 创建豆子充值订单
     * 服务端按 packageId 查套餐取 beans+price,校验上架,price 元×100 转分,
     * 调支付下单(productType=BEAN_RECHARGE),金额一律服务端计算,绝不信任前端。
     *
     * @param userId    当前用户ID
     * @param packageId 套餐ID
     * @return 商户订单号 + 调起支付参数
     */
    CreateOrderVO createRecharge(Long userId, Long packageId);
}
