package com.aurora.admin.mobile.vo;

import com.aurora.admin.mobile.domain.TemplateCategory;
import lombok.Data;

import java.io.Serializable;

/** 移动端行业分类视图。 */
@Data
public class TemplateCategoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long categoryId;
    private String name;
    private Integer sort;

    public static TemplateCategoryVO from(TemplateCategory category) {
        TemplateCategoryVO vo = new TemplateCategoryVO();
        vo.setCategoryId(category.getCategoryId());
        vo.setName(category.getName());
        vo.setSort(category.getSort());
        return vo;
    }
}
