package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.TemplateCategory;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/** 案例行业分类 Service。 */
public interface ITemplateCategoryService extends IService<TemplateCategory> {

    List<TemplateCategory> listCategories(String name, String status);

    List<TemplateCategory> listAvailable(String mediaType);

    void createCategory(TemplateCategory category);

    void updateCategory(TemplateCategory category);

    void removeCategory(Long categoryId);

    boolean categoryExists(Long categoryId);
}
