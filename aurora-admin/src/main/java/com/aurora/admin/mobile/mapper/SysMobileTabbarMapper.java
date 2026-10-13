package com.aurora.admin.mobile.mapper;

import com.aurora.admin.mobile.domain.SysMobileTabbar;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 移动端底部导航配置 Mapper
 */
@Mapper
public interface SysMobileTabbarMapper extends BaseMapper<SysMobileTabbar> {

    /** 编辑时锁定全部导航项，保证“至少一项启用”并发校验可靠。 */
    @Select("SELECT * FROM sys_mobile_tabbar WHERE del_flag = '0' ORDER BY sort, tab_id FOR UPDATE")
    List<SysMobileTabbar> selectAllForUpdate();
}
