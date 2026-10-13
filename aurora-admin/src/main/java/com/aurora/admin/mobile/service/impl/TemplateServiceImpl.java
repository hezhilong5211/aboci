package com.aurora.admin.mobile.service.impl;

import com.aurora.admin.mobile.domain.Template;
import com.aurora.admin.mobile.domain.TemplateCategory;
import com.aurora.admin.mobile.mapper.TemplateCategoryMapper;
import com.aurora.admin.mobile.mapper.TemplateMapper;
import com.aurora.admin.mobile.service.ITemplateService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 案例/模板 Service 实现
 * 逻辑删走 removeById(@TableLogic delFlag);params/result_media 以 String 原样存取 JSON。
 */
@Service
public class TemplateServiceImpl extends ServiceImpl<TemplateMapper, Template> implements ITemplateService {

    private final TemplateCategoryMapper templateCategoryMapper;

    public TemplateServiceImpl(TemplateCategoryMapper templateCategoryMapper) {
        this.templateCategoryMapper = templateCategoryMapper;
    }

    @Override
    public IPage<Template> pageList(Integer pageNum, Integer pageSize, String category,
                                    Long industryCategoryId, String status, String title) {
        LambdaQueryWrapper<Template> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(category), Template::getCategory, category)
                .eq(industryCategoryId != null, Template::getIndustryCategoryId, industryCategoryId)
                .eq(StringUtils.hasText(status), Template::getStatus, status)
                .like(StringUtils.hasText(title), Template::getTitle, title)
                .orderByAsc(Template::getSort)
                .orderByDesc(Template::getCreateTime);
        IPage<Template> result = page(new Page<>(pageNum, pageSize), wrapper);
        fillIndustryCategoryNames(result.getRecords());
        return result;
    }

    @Override
    public void incrementUseCount(Long templateId) {
        // SQL 原子自增,避免并发覆盖;@TableLogic 自动追加 del_flag 过滤(已删模板不自增)
        LambdaUpdateWrapper<Template> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Template::getTemplateId, templateId)
                .setSql("use_count = use_count + 1");
        update(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setFeatured(List<Long> ids) {
        // 1. 先把所有精选清零(唯一性:精选集合整体替换)
        LambdaUpdateWrapper<Template> clear = new LambdaUpdateWrapper<>();
        clear.eq(Template::getIsFeatured, 1).set(Template::getIsFeatured, 0);
        update(clear);
        // 2. 再把传入 ids 置精选(空则仅清空)
        if (ids != null && !ids.isEmpty()) {
            LambdaUpdateWrapper<Template> set = new LambdaUpdateWrapper<>();
            set.in(Template::getTemplateId, ids).set(Template::getIsFeatured, 1);
            update(set);
        }
    }

    @Override
    public List<Template> listFeatured(String category, Long industryCategoryId, int limit) {
        // 仅返回后台手动设的精选(is_featured=1 且上架),按 sort 升序、create_time 升序,至多 limit 条;
        // 不再自动兜底——无精选则返回空,C 端轮播不显示(由后台手动控制增删)。
        LambdaQueryWrapper<Template> w = new LambdaQueryWrapper<>();
        w.eq(Template::getStatus, "0")
                .eq(StringUtils.hasText(category), Template::getCategory, category)
                .eq(industryCategoryId != null, Template::getIndustryCategoryId, industryCategoryId)
                .eq(Template::getIsFeatured, 1)
                .orderByAsc(Template::getSort)
                .orderByAsc(Template::getCreateTime)
                .last("LIMIT " + limit);
        List<Template> result = list(w);
        fillIndustryCategoryNames(result);
        return result;
    }

    @Override
    public void fillIndustryCategoryNames(List<Template> templates) {
        if (templates == null || templates.isEmpty()) return;
        List<Long> categoryIds = templates.stream()
                .map(Template::getIndustryCategoryId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (categoryIds.isEmpty()) return;
        List<TemplateCategory> categories = templateCategoryMapper.selectBatchIds(categoryIds);
        Map<Long, TemplateCategory> categoryMap = (categories == null ? Collections.<TemplateCategory>emptyList() : categories)
                .stream()
                .collect(Collectors.toMap(TemplateCategory::getCategoryId, Function.identity()));
        templates.forEach(template -> {
            TemplateCategory category = categoryMap.get(template.getIndustryCategoryId());
            template.setIndustryCategoryName(category == null ? null : category.getName());
        });
    }

    @Override
    public boolean save(Template entity) {
        unescapeJsonFields(entity);
        return super.save(entity);
    }

    @Override
    public boolean updateById(Template entity) {
        unescapeJsonFields(entity);
        return super.updateById(entity);
    }

    /**
     * params/result_media 存的是 JSON 字符串,XSS 过滤器会把其中的 " 转成 &quot; 破坏 JSON;
     * 入库前仅对这两个 JSON 字段反转义恢复合法 JSON(prompt/title 等仍保留 XSS 转义)。
     */
    private void unescapeJsonFields(Template entity) {
        if (entity.getParams() != null) {
            entity.setParams(HtmlUtils.htmlUnescape(entity.getParams()));
        }
        if (entity.getResultMedia() != null) {
            entity.setResultMedia(HtmlUtils.htmlUnescape(entity.getResultMedia()));
        }
        if (entity.getReferenceImages() != null) {
            entity.setReferenceImages(HtmlUtils.htmlUnescape(entity.getReferenceImages()));
        }
    }
}
