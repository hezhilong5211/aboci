package com.aurora.admin.mobile.service.impl;

import com.aurora.admin.mobile.domain.PayOrder;
import com.aurora.admin.mobile.mapper.PayOrderMapper;
import com.aurora.admin.mobile.service.IPayOrderService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 支付订单 Service 实现类
 */
@Service
public class PayOrderServiceImpl extends ServiceImpl<PayOrderMapper, PayOrder>
        implements IPayOrderService {

    @Override
    public IPage<PayOrder> pageOrders(Integer status, String productType, String keyword,
                                      LocalDateTime beginTime, LocalDateTime endTime,
                                      Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<PayOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, PayOrder::getStatus, status);
        wrapper.eq(StringUtils.hasText(productType), PayOrder::getProductType, productType);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 关键词:匹配订单号(模糊);纯数字时再 OR 精确匹配用户ID
            wrapper.and(w -> {
                w.like(PayOrder::getOrderNo, kw);
                if (kw.chars().allMatch(Character::isDigit)) {
                    w.or().eq(PayOrder::getUserId, Long.valueOf(kw));
                }
            });
        }
        wrapper.ge(beginTime != null, PayOrder::getCreateTime, beginTime);
        wrapper.le(endTime != null, PayOrder::getCreateTime, endTime);
        wrapper.orderByDesc(PayOrder::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }
}
