package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.SysMobileTabbar;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 移动端底部导航配置 Service 接口
 */
public interface ISysMobileTabbarService extends IService<SysMobileTabbar> {

    /**
     * 查询全部 Tab(按 sort 升序)
     */
    List<SysMobileTabbar> listAllOrderBySort();

    /**
     * 编辑可改字段(名称/双态图标/显隐/排序);tab_key、page_path 只读不更新。
     *
     * @return 是否更新成功
     */
    boolean editEditable(SysMobileTabbar body);
}
