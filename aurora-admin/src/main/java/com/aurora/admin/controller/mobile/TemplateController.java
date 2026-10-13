package com.aurora.admin.controller.mobile;

import com.aurora.admin.mobile.domain.Template;
import com.aurora.admin.mobile.dto.TemplateStatusDTO;
import com.aurora.admin.mobile.service.ITemplateService;
import com.aurora.admin.mobile.service.ITemplateCategoryService;
import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.mapper.SysAiModelMapper;
import com.aurora.common.core.annotation.Log;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.enums.BusinessType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 案例/模板 管理后台控制器(CRUD + 上下架)
 * 本期仅管理后台,不含移动端读接口;删除为逻辑删(@TableLogic)。
 */
@Tag(name = "案例/模板", description = "案例/模板管理 CRUD")
@RestController
@RequestMapping("/system/template")
@RequiredArgsConstructor
public class TemplateController {

    private final ITemplateService templateService;
    private final ITemplateCategoryService templateCategoryService;
    private final SysAiModelMapper sysAiModelMapper;

    /**
     * 取当前默认图像模型的画幅比例集合(供编辑模板时画幅下拉)。
     * 默认模型优先(is_default=1, type=image, active),无默认按 sort 取第一条;返回 supportedAspectRatios(逗号分隔)。
     */
    @Operation(summary = "默认图像模型画幅", description = "返回默认图像模型的 supportedAspectRatios,供模板画幅下拉")
    @GetMapping("/imageRatios")
    @PreAuthorize("@ss.hasPermi('template:query')")
    public Result<Map<String, Object>> defaultImageRatios() {
        SysAiModel model = sysAiModelMapper.selectOne(new LambdaQueryWrapper<SysAiModel>()
                .eq(SysAiModel::getModelType, "image")
                .eq(SysAiModel::getIsActive, 1)
                .orderByDesc(SysAiModel::getIsDefault)
                .orderByAsc(SysAiModel::getSortOrder)
                .last("LIMIT 1"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("modelName", model == null ? null : model.getModelName());
        data.put("supportedAspectRatios", model == null ? null : model.getSupportedAspectRatios());
        return Result.success(data);
    }

    /**
     * 分页查询模板列表(按 category/status 精确、title 模糊筛选)
     */
    @Operation(summary = "分页查询列表", description = "按分类/上下架精确、标题模糊筛选;sort 升序、create_time 降序兜底")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('template:list')")
    public Result<IPage<Template>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long industryCategoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.success(templateService.pageList(
                pageNum, pageSize, category, industryCategoryId, status, keyword));
    }

    /**
     * 模板详情
     */
    @Operation(summary = "模板详情")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('template:query')")
    public Result<Template> getInfo(@PathVariable Long id) {
        Template template = templateService.getById(id);
        templateService.fillIndustryCategoryNames(template == null ? List.of() : List.of(template));
        return Result.success(template);
    }

    /**
     * 新增模板
     */
    @Operation(summary = "新增模板")
    @PostMapping
    @PreAuthorize("@ss.hasPermi('template:add')")
    @Log(title = "案例/模板", businessType = BusinessType.INSERT)
    public Result<Void> add(@RequestBody Template template) {
        String error = validate(template);
        if (error != null) {
            return Result.error(error);
        }
        clearServerControlledFields(template);
        template.setTemplateId(null);
        templateService.save(template);
        return Result.success();
    }

    /**
     * 修改模板
     */
    @Operation(summary = "修改模板")
    @PutMapping
    @PreAuthorize("@ss.hasPermi('template:edit')")
    @Log(title = "案例/模板", businessType = BusinessType.UPDATE)
    public Result<Void> edit(@RequestBody Template template) {
        if (template.getTemplateId() == null) {
            return Result.error("模板ID不能为空");
        }
        String error = validate(template);
        if (error != null) {
            return Result.error(error);
        }
        clearServerControlledFields(template);
        templateService.updateById(template);
        return Result.success();
    }

    /**
     * 设置本周精选(后台「本周精选」按钮):传 id 数组(≤5),整体替换精选集合(空=取消全部精选)。
     */
    @Operation(summary = "设置本周精选", description = "传 templateId 数组(最多5),整体替换精选集合")
    @PutMapping("/featured")
    @PreAuthorize("@ss.hasPermi('template:edit')")
    @Log(title = "案例/模板", businessType = BusinessType.UPDATE)
    public Result<Void> setFeatured(@RequestBody(required = false) List<Long> ids) {
        if (ids != null && ids.size() > 5) {
            return Result.error("本周精选最多 5 个");
        }
        templateService.setFeatured(ids);
        return Result.success();
    }

    /**
     * 上下架快捷切换
     */
    @Operation(summary = "上下架切换", description = "传 {templateId, status} 快捷切换上下架")
    @PutMapping("/status")
    @PreAuthorize("@ss.hasPermi('template:edit')")
    public Result<Void> changeStatus(@RequestBody TemplateStatusDTO dto) {
        if (dto.getTemplateId() == null) {
            return Result.error("模板ID不能为空");
        }
        if (!"0".equals(dto.getStatus()) && !"1".equals(dto.getStatus())) {
            return Result.error("状态非法,仅支持 0上架/1下架");
        }
        Template update = new Template();
        update.setTemplateId(dto.getTemplateId());
        update.setStatus(dto.getStatus());
        templateService.updateById(update);
        return Result.success();
    }

    /**
     * 删除模板(逻辑删)
     */
    @Operation(summary = "删除模板", description = "逻辑删,@TableLogic 标记 del_flag")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('template:remove')")
    @Log(title = "案例/模板", businessType = BusinessType.DELETE)
    public Result<Void> remove(@PathVariable Long id) {
        templateService.removeById(id);
        return Result.success();
    }

    /**
     * 入参校验:category 必须 image/video;title、prompt 非空。
     *
     * @return 错误文案;校验通过返回 null
     */
    private String validate(Template template) {
        if (!"image".equals(template.getCategory()) && !"video".equals(template.getCategory())) {
            return "分类非法,仅支持 image/video";
        }
        if (!StringUtils.hasText(template.getTitle())) {
            return "标题不能为空";
        }
        if (!StringUtils.hasText(template.getPrompt())) {
            return "提示词不能为空";
        }
        if (!templateCategoryService.categoryExists(template.getIndustryCategoryId())) {
            return "请选择有效的行业分类";
        }
        return null;
    }

    /**
     * 清空服务端控制字段,防止前端篡改审计/逻辑删字段(R-DTO 防 Mass-Assignment)。
     * 审计字段交由 MybatisMetaObjectHandler 自动回填,删除标记由 @TableLogic 管理。
     */
    private void clearServerControlledFields(Template template) {
        template.setCreateBy(null);
        template.setCreateTime(null);
        template.setUpdateBy(null);
        template.setUpdateTime(null);
        template.setDelFlag(null);
        template.setIndustryCategoryName(null);
    }
}
