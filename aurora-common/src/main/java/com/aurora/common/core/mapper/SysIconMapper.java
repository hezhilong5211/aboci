package com.aurora.common.core.mapper;

import com.aurora.common.core.domain.entity.SysIcon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 自定义图标表 Mapper 接口
 */
@Mapper
public interface SysIconMapper extends BaseMapper<SysIcon> {

    /**
     * 获取下一个自动序号（用于自动命名）
     * 
     * @return 当前最大序号+1，如果没有记录返回1
     */
    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(icon_name, 6) AS UNSIGNED)), 0) + 1 " +
            "FROM sys_icon WHERE icon_name LIKE 'icon_%' AND del_flag = '0'")
    Integer getNextSequence();
}
