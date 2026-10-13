package com.aurora.admin.mobile.vo;

import com.aurora.admin.mobile.domain.MembershipLevel;
import lombok.Data;

/**
 * 移动端用户列表可选择的已启用会员等级。
 */
@Data
public class MembershipLevelOptionVO {

    private Long levelId;

    private String name;

    public static MembershipLevelOptionVO from(MembershipLevel level) {
        MembershipLevelOptionVO vo = new MembershipLevelOptionVO();
        vo.setLevelId(level.getLevelId());
        vo.setName(level.getName());
        return vo;
    }
}
