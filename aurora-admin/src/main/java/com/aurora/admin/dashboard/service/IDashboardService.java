package com.aurora.admin.dashboard.service;

import com.aurora.admin.dashboard.vo.DashboardVO;

/**
 * 后台首页统计 Service
 */
public interface IDashboardService {

    /**
     * 聚合首页数据概览
     *
     * @return 首页概览出参
     */
    DashboardVO overview();
}
