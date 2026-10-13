package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.Template;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 案例/模板 Service 接口
 */
public interface ITemplateService extends IService<Template> {

    /**
     * 分页查询模板列表。
     * 按 category/status 精确、title 模糊筛选;按 sort 升序、create_time 降序兜底排序。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param category 分类(image/video),为空不筛
     * @param status   上下架(0/1),为空不筛
     * @param title    标题关键词(模糊),为空不筛
     * @return 分页结果
     */
    IPage<Template> pageList(Integer pageNum, Integer pageSize, String category,
                             Long industryCategoryId, String status, String title);

    /**
     * 使用次数 +1(C 端「用此模板带入生成」时调用)。
     * 用 SQL 原子自增(use_count = use_count + 1),避免并发读改写覆盖。
     *
     * @param templateId 模板 id
     */
    void incrementUseCount(Long templateId);

    /**
     * 设置本周精选(后台「本周精选」按钮):先把所有精选清零,再把传入 ids 置精选。
     * 数量上限由调用方(Controller)校验(≤5)。
     *
     * @param ids 选为精选的模板 id 列表(空=取消全部精选)
     */
    void setFeatured(List<Long> ids);

    /**
     * C 端精选轮播:仅取上架的手动精选(is_featured=1)按 sort/create_time,至多 limit 条;
     * 无精选则返回空(不兜底,C 端轮播不显示),由后台手动控制增删。
     *
     * @param category 分类(image/video),为空不筛
     * @param limit    最多条数(如 5)
     * @return 精选列表(可能为空)
     */
    List<Template> listFeatured(String category, Long industryCategoryId, int limit);

    /** 批量补充行业分类名称，避免列表逐条查询。 */
    void fillIndustryCategoryNames(List<Template> templates);
}
