package com.aurora.admin.mobile.controller;

import com.aurora.admin.mobile.domain.Template;
import com.aurora.admin.mobile.service.ITemplateCategoryService;
import com.aurora.admin.mobile.service.ITemplateService;
import com.aurora.admin.mobile.vo.TemplateCategoryVO;
import com.aurora.admin.mobile.vo.TemplateVO;
import com.aurora.common.core.domain.Result;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 移动端案例/模板控制器(C 端,公开只读 + 使用计数,免鉴权)。
 *
 * <p>仅暴露上架(status='0')模板;/api/mobile/template/** 已在 SecurityConfig 放行(公开内容,未登录可浏览)。
 * 复用 admin 侧 {@link ITemplateService}(分页/自增),不另建实体/表。</p>
 */
@Tag(name = "移动端案例/模板", description = "C 端案例库列表/详情/使用计数")
@RestController
@RequestMapping("/api/mobile/template")
@RequiredArgsConstructor
public class MobileTemplateController {

    private final ITemplateService templateService;
    private final ITemplateCategoryService templateCategoryService;

    /** 上架状态码(0 上架 / 1 下架) */
    private static final String STATUS_ON_SHELF = "0";

    /**
     * 案例列表分页:仅上架,可按 category(image/video)筛选,按 sort 升序、create_time 倒序。
     */
    @Operation(summary = "案例列表", description = "仅上架模板,可按 category(image/video)筛选")
    @GetMapping("/list")
    public Result<IPage<TemplateVO>> list(@RequestParam(required = false) String category,
                                          @RequestParam(required = false) Long industryCategoryId,
                                          @RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "10") Integer pageSize) {
        IPage<Template> page = templateService.pageList(
                pageNum, pageSize, category, industryCategoryId, STATUS_ON_SHELF, null);
        return Result.success(page.convert(TemplateVO::from));
    }

    /** 当前媒体类型下存在上架案例的启用行业分类。 */
    @Operation(summary = "行业分类")
    @GetMapping("/categories")
    public Result<List<TemplateCategoryVO>> categories(
            @RequestParam(defaultValue = "image") String mediaType) {
        return Result.success(templateCategoryService.listAvailable(mediaType).stream()
                .map(TemplateCategoryVO::from)
                .collect(Collectors.toList()));
    }

    /**
     * 精选轮播:顶部「本周精选」轮播(最多 5),精选优先,无精选则兜底取该分类最早添加的 5 张;空则返回空数组。
     */
    @Operation(summary = "精选轮播", description = "顶部精选轮播(≤5):精选优先,无则兜底最早5张")
    @GetMapping("/featured")
    public Result<List<TemplateVO>> featured(@RequestParam(required = false) String category,
                                             @RequestParam(required = false) Long industryCategoryId) {
        List<Template> list = templateService.listFeatured(category, industryCategoryId, 5);
        return Result.success(list.stream().map(TemplateVO::from).collect(Collectors.toList()));
    }

    /**
     * 案例详情:仅返回上架且未删的模板;不存在/已下架返回错误,不泄露后台数据。
     */
    @Operation(summary = "案例详情")
    @GetMapping("/{id}")
    public Result<TemplateVO> detail(@PathVariable("id") Long id) {
        // getById 经 @TableLogic 自动过滤 del_flag(已删返回 null);再校验上架
        Template t = templateService.getById(id);
        if (t == null || !STATUS_ON_SHELF.equals(t.getStatus())) {
            return Result.error("案例不存在或已下架");
        }
        templateService.fillIndustryCategoryNames(List.of(t));
        return Result.success(TemplateVO.from(t));
    }

    /**
     * 使用计数 +1(C 端「用此模板带入生成」时调用)。
     */
    @Operation(summary = "使用计数+1")
    @PostMapping("/{id}/use")
    public Result<Void> use(@PathVariable("id") Long id) {
        templateService.incrementUseCount(id);
        return Result.success();
    }
}
