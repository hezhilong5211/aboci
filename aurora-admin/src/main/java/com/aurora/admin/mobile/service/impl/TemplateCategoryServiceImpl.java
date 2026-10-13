package com.aurora.admin.mobile.service.impl;

import com.aurora.admin.mobile.domain.Template;
import com.aurora.admin.mobile.domain.TemplateCategory;
import com.aurora.admin.mobile.mapper.TemplateCategoryMapper;
import com.aurora.admin.mobile.service.ITemplateCategoryService;
import com.aurora.admin.mobile.service.ITemplateService;
import com.aurora.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** 案例行业分类 Service 实现。 */
@Service
@RequiredArgsConstructor
public class TemplateCategoryServiceImpl
        extends ServiceImpl<TemplateCategoryMapper, TemplateCategory>
        implements ITemplateCategoryService {

    private final ITemplateService templateService;

    @Override
    public List<TemplateCategory> listCategories(String name, String status) {
        return list(new LambdaQueryWrapper<TemplateCategory>()
                .like(StringUtils.hasText(name), TemplateCategory::getName, name)
                .eq(StringUtils.hasText(status), TemplateCategory::getStatus, status)
                .orderByAsc(TemplateCategory::getSort)
                .orderByAsc(TemplateCategory::getCategoryId));
    }

    @Override
    public List<TemplateCategory> listAvailable(String mediaType) {
        if (!"image".equals(mediaType) && !"video".equals(mediaType)) {
            throw new ServiceException("媒体类型非法");
        }
        return baseMapper.selectAvailableByMediaType(mediaType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createCategory(TemplateCategory category) {
        normalizeAndValidate(category, null);
        category.setCategoryId(null);
        save(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(TemplateCategory category) {
        if (category == null || category.getCategoryId() == null || getById(category.getCategoryId()) == null) {
            throw new ServiceException("行业分类不存在");
        }
        normalizeAndValidate(category, category.getCategoryId());
        updateById(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeCategory(Long categoryId) {
        if (categoryId == null || getById(categoryId) == null) {
            throw new ServiceException("行业分类不存在");
        }
        long used = templateService.count(new LambdaQueryWrapper<Template>()
                .eq(Template::getIndustryCategoryId, categoryId));
        if (used > 0) {
            throw new ServiceException("该分类已有案例使用，请先调整案例分类");
        }
        removeById(categoryId);
    }

    @Override
    public boolean categoryExists(Long categoryId) {
        return categoryId != null && getById(categoryId) != null;
    }

    private void normalizeAndValidate(TemplateCategory category, Long excludeId) {
        if (category == null || !StringUtils.hasText(category.getName())) {
            throw new ServiceException("分类名称不能为空");
        }
        String name = category.getName().trim();
        if (name.length() > 32) {
            throw new ServiceException("分类名称不能超过32个字");
        }
        if (!"0".equals(category.getStatus()) && !"1".equals(category.getStatus())) {
            throw new ServiceException("分类状态非法");
        }
        Long duplicate = count(new LambdaQueryWrapper<TemplateCategory>()
                .eq(TemplateCategory::getName, name)
                .ne(excludeId != null, TemplateCategory::getCategoryId, excludeId));
        if (duplicate != null && duplicate > 0) {
            throw new ServiceException("分类名称已存在");
        }
        category.setName(name);
        if (category.getSort() == null) category.setSort(0);
        category.setCreateBy(null);
        category.setCreateTime(null);
        category.setUpdateBy(null);
        category.setUpdateTime(null);
        category.setDelFlag(null);
    }
}
