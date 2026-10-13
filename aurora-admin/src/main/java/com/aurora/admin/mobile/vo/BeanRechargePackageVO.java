package com.aurora.admin.mobile.vo;

import com.aurora.admin.mobile.domain.BeanRechargePackage;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** C 端豆子充值套餐视图，不暴露微信道具 ID、审计字段和逻辑删除字段。 */
@Data
public class BeanRechargePackageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long packageId;
    private String name;
    private Long beans;
    private BigDecimal price;
    private Integer sort;

    public static BeanRechargePackageVO from(BeanRechargePackage source) {
        BeanRechargePackageVO vo = new BeanRechargePackageVO();
        vo.setPackageId(source.getPackageId());
        vo.setName(source.getName());
        vo.setBeans(source.getBeans());
        vo.setPrice(source.getPrice());
        vo.setSort(source.getSort());
        return vo;
    }
}
