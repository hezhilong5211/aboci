package com.aurora.admin.service;

import com.aurora.common.core.domain.entity.SysDept;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 部门 Service 接口
 */
public interface SysDeptService extends IService<SysDept> {

    /**
     * 查询部门树形列表
     */
    List<SysDept> selectDeptTree();

    /**
     * 根据部门ID查询部门信息
     */
    SysDept selectDeptById(Long deptId);

    /**
     * 新增部门
     */
    int insertDept(SysDept dept);

    /**
     * 修改部门
     */
    int updateDept(SysDept dept);

    /**
     * 删除部门
     */
    int deleteDeptById(Long deptId);

    /**
     * 校验部门名称是否唯一
     */
    boolean checkDeptNameUnique(SysDept dept);

    /**
     * 查询部门是否存在用户
     */
    boolean checkDeptExistUser(Long deptId);

    /**
     * 查询部门是否有子部门
     */
    boolean hasChildDept(Long deptId);
}
