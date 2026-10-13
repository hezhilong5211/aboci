package com.aurora.common.core.mapper;

import com.aurora.common.core.domain.entity.SysDept;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 部门 Mapper 接口
 */
@Mapper
public interface SysDeptMapper extends BaseMapper<SysDept> {

    /**
     * 查询部门列表
     */
    @Select("SELECT * FROM sys_dept WHERE del_flag = '0' ORDER BY parent_id, order_num")
    List<SysDept> selectDeptList();

    /**
     * 根据部门ID查询子部门数量
     */
    @Select("SELECT COUNT(1) FROM sys_dept WHERE parent_id = #{deptId} AND del_flag = '0'")
    int selectChildCountByDeptId(@Param("deptId") Long deptId);

    /**
     * 查询部门及其所有子部门ID
     */
    @Select("SELECT dept_id FROM sys_dept WHERE FIND_IN_SET(#{deptId}, ancestors) AND del_flag = '0'")
    List<Long> selectChildDeptIds(@Param("deptId") Long deptId);

    /**
     * 根据角色ID查询部门树信息
     */
    @Select("SELECT d.dept_id FROM sys_dept d " +
            "LEFT JOIN sys_role_dept rd ON d.dept_id = rd.dept_id " +
            "WHERE rd.role_id = #{roleId} AND d.del_flag = '0' " +
            "ORDER BY d.parent_id, d.order_num")
    List<Long> selectDeptIdsByRoleId(@Param("roleId") Long roleId);
}
