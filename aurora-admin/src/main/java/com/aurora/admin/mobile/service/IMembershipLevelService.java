package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.MembershipLevel;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 会员等级配置 Service 接口
 */
public interface IMembershipLevelService extends IService<MembershipLevel> {

    /**
     * 查询等级列表(按 sort 升序,未删除)。
     *
     * @return 等级列表
     */
    List<MembershipLevel> listBySort();

    /**
     * 查询可购买等级列表(status=0 上架,按 sort 升序,未删除)。C 端用。
     *
     * @return 可购买等级列表
     */
    List<MembershipLevel> listOnSale();
}
