package com.aurora.admin.service.impl;

import com.aurora.admin.service.SysDeptService;
import com.aurora.common.core.domain.entity.SysDept;
import com.aurora.common.core.mapper.SysDeptMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 部门 Service 实现类
 */
@Service
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements SysDeptService {

    @Autowired
    private SysDeptMapper deptMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 查询部门树形列表
     */
    @Override
    public List<SysDept> selectDeptTree() {
        List<SysDept> deptList = deptMapper.selectDeptList();
        return buildDeptTree(deptList);
    }

    /**
     * 构建部门树形结构
     */
    private List<SysDept> buildDeptTree(List<SysDept> deptList) {
        List<SysDept> result = new ArrayList<>();
        List<Long> tempList = deptList.stream().map(SysDept::getDeptId).collect(Collectors.toList());

        for (SysDept dept : deptList) {
            // 如果是顶级节点，遍历该父节点的所有子节点
            if (!tempList.contains(dept.getParentId())) {
                recursionFn(deptList, dept);
                result.add(dept);
            }
        }

        if (result.isEmpty()) {
            result = deptList;
        }
        return result;
    }

    /**
     * 递归列表
     */
    private void recursionFn(List<SysDept> list, SysDept parent) {
        // 得到子节点列表
        List<SysDept> childList = getChildList(list, parent);
        parent.setChildren(childList);
        for (SysDept child : childList) {
            if (hasChild(list, child)) {
                recursionFn(list, child);
            }
        }
    }

    /**
     * 得到子节点列表
     */
    private List<SysDept> getChildList(List<SysDept> list, SysDept parent) {
        return list.stream()
                .filter(d -> d.getParentId() != null && d.getParentId().equals(parent.getDeptId()))
                .collect(Collectors.toList());
    }

    /**
     * 判断是否有子节点
     */
    private boolean hasChild(List<SysDept> list, SysDept parent) {
        return getChildList(list, parent).size() > 0;
    }

    /**
     * 根据部门ID查询部门信息
     */
    @Override
    public SysDept selectDeptById(Long deptId) {
        return deptMapper.selectById(deptId);
    }

    /**
     * 新增部门
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertDept(SysDept dept) {
        // 设置祖级列表
        SysDept parentDept = deptMapper.selectById(dept.getParentId());
        if (parentDept != null) {
            dept.setAncestors(parentDept.getAncestors() + "," + dept.getParentId());
        } else {
            dept.setAncestors("0");
        }
        dept.setCreateTime(LocalDateTime.now());
        return deptMapper.insert(dept);
    }

    /**
     * 修改部门
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateDept(SysDept dept) {
        SysDept newParentDept = deptMapper.selectById(dept.getParentId());
        SysDept oldDept = deptMapper.selectById(dept.getDeptId());

        if (newParentDept != null && oldDept != null) {
            String newAncestors = newParentDept.getAncestors() + "," + newParentDept.getDeptId();
            String oldAncestors = oldDept.getAncestors();
            dept.setAncestors(newAncestors);
            // 更新子部门的祖级列表
            updateDeptChildren(dept.getDeptId(), newAncestors, oldAncestors);
        }
        dept.setUpdateTime(LocalDateTime.now());
        return deptMapper.updateById(dept);
    }

    /**
     * 修改子元素祖级关系
     */
    private void updateDeptChildren(Long deptId, String newAncestors, String oldAncestors) {
        List<SysDept> children = deptMapper.selectList(
                new LambdaQueryWrapper<SysDept>()
                        .apply("FIND_IN_SET({0}, ancestors)", deptId));
        for (SysDept child : children) {
            child.setAncestors(child.getAncestors().replaceFirst(oldAncestors, newAncestors));
            deptMapper.updateById(child);
        }
    }

    /**
     * 删除部门
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDeptById(Long deptId) {
        return deptMapper.deleteById(deptId);
    }

    /**
     * 校验部门名称是否唯一
     */
    @Override
    public boolean checkDeptNameUnique(SysDept dept) {
        Long deptId = dept.getDeptId() == null ? -1L : dept.getDeptId();
        SysDept info = deptMapper.selectOne(
                new LambdaQueryWrapper<SysDept>()
                        .eq(SysDept::getDeptName, dept.getDeptName())
                        .eq(SysDept::getParentId, dept.getParentId()));
        if (info != null && !info.getDeptId().equals(deptId)) {
            return false;
        }
        return true;
    }

    /**
     * 查询部门是否存在用户
     */
    @Override
    public boolean checkDeptExistUser(Long deptId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_user WHERE dept_id = ? AND del_flag = '0'",
                Integer.class, deptId);
        return count != null && count > 0;
    }

    /**
     * 查询部门是否有子部门
     */
    @Override
    public boolean hasChildDept(Long deptId) {
        int count = deptMapper.selectChildCountByDeptId(deptId);
        return count > 0;
    }
}
