package com.aurora.admin.mobile.mapper;

import com.aurora.admin.mobile.domain.TemplateCategory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 案例行业分类 Mapper。 */
@Mapper
public interface TemplateCategoryMapper extends BaseMapper<TemplateCategory> {

    /** 只返回当前媒体类型下确有上架案例的启用分类，避免小程序出现空分类。 */
    @Select("SELECT c.* FROM sys_template_category c "
            + "WHERE c.del_flag = '0' AND c.status = '0' "
            + "AND EXISTS (SELECT 1 FROM template t WHERE t.industry_category_id = c.category_id "
            + "AND t.category = #{mediaType} AND t.status = '0' AND t.del_flag = '0') "
            + "ORDER BY c.sort ASC, c.category_id ASC")
    List<TemplateCategory> selectAvailableByMediaType(@Param("mediaType") String mediaType);
}
