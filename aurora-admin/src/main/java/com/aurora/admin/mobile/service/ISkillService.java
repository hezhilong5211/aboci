package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.Skill;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * Skill 配置 Service 接口
 */
public interface ISkillService extends IService<Skill> {

    /**
     * 分页查询 Skill 列表。
     * 按 name 模糊、status 精确筛选;按 sort 升序、create_time 降序兜底排序。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param name     名称关键词(模糊),为空不筛
     * @param status   状态(0启用/1停用),为空不筛
     * @return 分页结果
     */
    IPage<Skill> pageList(Integer pageNum, Integer pageSize, String name, String status);
}
