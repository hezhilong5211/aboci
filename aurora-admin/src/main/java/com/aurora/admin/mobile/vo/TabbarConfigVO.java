package com.aurora.admin.mobile.vo;

import com.aurora.admin.mobile.domain.SysMobileTabbar;
import lombok.Data;

import java.util.List;

/**
 * 移动端底部导航取数出参
 * 顶层颜色为固定值;list 字段名严格对齐 hagent normalizeTabbarConfig 期望
 * (key/pagePath/text/iconPath/selectedIconPath/visible/sort)。
 */
@Data
public class TabbarConfigVO {

    /** 普通态文字颜色 */
    private String color = "#999999";

    /** 选中态文字颜色 */
    private String selectedColor = "#4C6EF5";

    /** 背景色 */
    private String backgroundColor = "#ffffff";

    /** Tab 列表 */
    private List<TabbarItemVO> list;

    /**
     * 单个 Tab 出参,字段名对齐移动端 normalizeTabbarConfig。
     */
    @Data
    public static class TabbarItemVO {

        /** Tab 唯一标识 */
        private String key;

        /** 页面路径 */
        private String pagePath;

        /** 名称 */
        private String text;

        /** 普通态图标URL */
        private String iconPath;

        /** 选中态图标URL */
        private String selectedIconPath;

        /** 是否显示 */
        private boolean visible;

        /** 排序号 */
        private Integer sort;

        /**
         * 由配置实体构建单个 Tab 出参
         */
        public static TabbarItemVO from(SysMobileTabbar tab) {
            TabbarItemVO vo = new TabbarItemVO();
            vo.setKey(tab.getTabKey());
            vo.setPagePath(tab.getPagePath());
            vo.setText(tab.getTabName());
            vo.setIconPath(tab.getIconNormal());
            vo.setSelectedIconPath(tab.getIconActive());
            vo.setVisible(tab.getVisible() != null && tab.getVisible() == 1);
            vo.setSort(tab.getSort());
            return vo;
        }
    }

    /**
     * 由全部 Tab 配置构建出参(全返回,显隐交前端过滤)。
     */
    public static TabbarConfigVO from(List<SysMobileTabbar> tabs) {
        TabbarConfigVO vo = new TabbarConfigVO();
        vo.setList(tabs.stream().map(TabbarItemVO::from).toList());
        return vo;
    }
}
