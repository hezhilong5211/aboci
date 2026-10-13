package com.aurora.admin.mobile.vo;

import com.aurora.admin.mobile.domain.Template;
import lombok.Data;

import java.io.Serializable;

/**
 * 案例/模板 C 端视图:仅暴露公开展示字段,剔除审计字段与 del_flag。
 * params/result_media 为 JSON 字符串原样透传,前端按 category 自行解析(image/video 结构不同)。
 */
@Data
public class TemplateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模板ID */
    private Long templateId;
    /** 分类(image 图片 / video 视频) */
    private String category;
    /** 行业分类ID */
    private Long industryCategoryId;
    /** 行业分类名称 */
    private String industryCategoryName;
    /** 案例标题 */
    private String title;
    /** 封面图 URL */
    private String coverUrl;
    /** 提示词文本 */
    private String prompt;
    /** 参数预设(JSON 字符串,按 category 不同) */
    private String params;
    /** 效果媒体 URL 列表(JSON 数组字符串) */
    private String resultMedia;
    /** 图生图模板预置参考图(JSON 数组字符串,/profile URL) */
    private String referenceImages;
    /** 标签(逗号分隔) */
    private String tags;
    /** 使用次数 */
    private Long useCount;
    /** 排序(小在前) */
    private Integer sort;

    /**
     * 实体 → C 端视图。
     */
    public static TemplateVO from(Template t) {
        TemplateVO vo = new TemplateVO();
        vo.setTemplateId(t.getTemplateId());
        vo.setCategory(t.getCategory());
        vo.setIndustryCategoryId(t.getIndustryCategoryId());
        vo.setIndustryCategoryName(t.getIndustryCategoryName());
        vo.setTitle(t.getTitle());
        vo.setCoverUrl(t.getCoverUrl());
        vo.setPrompt(t.getPrompt());
        vo.setParams(t.getParams());
        vo.setResultMedia(t.getResultMedia());
        vo.setReferenceImages(t.getReferenceImages());
        vo.setTags(t.getTags());
        vo.setUseCount(t.getUseCount());
        vo.setSort(t.getSort());
        return vo;
    }
}
