package com.aurora.admin.mobile.vo;

import com.aurora.admin.mobile.domain.MembershipLevel;
import lombok.Data;

import java.math.BigDecimal;

/**
 * C 端会员等级出参(可购买等级列表展示用)
 * 仅暴露购买页所需字段,不外泄审计/逻辑删等内部字段。
 */
@Data
public class MembershipLevelVO {

    /** 等级ID */
    private Long levelId;

    /** 等级名称 */
    private String name;

    /** 价格(单位:元) */
    private BigDecimal price;

    /** 会员时长(天) */
    private Integer durationDays;

    /** 每周期送豆数 */
    private Long grantBeans;

    /** 会员卡片背景图URL */
    private String cardBg;

    /**
     * 由等级实体构造
     */
    public static MembershipLevelVO from(MembershipLevel level) {
        MembershipLevelVO vo = new MembershipLevelVO();
        vo.setLevelId(level.getLevelId());
        vo.setName(level.getName());
        vo.setPrice(level.getPrice());
        vo.setDurationDays(level.getDurationDays());
        vo.setGrantBeans(level.getGrantBeans());
        vo.setCardBg(level.getCardBg());
        return vo;
    }
}
