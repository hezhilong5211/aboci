package com.aurora.admin.mobile.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 移动端底部导航(tabbar)配置实体 sys_mobile_tabbar
 * tab_key / page_path 由前端固定、只读;后台仅可编辑名称、双态图标、显隐、排序。
 */
@Data
@NoArgsConstructor
@TableName("sys_mobile_tabbar")
public class SysMobileTabbar implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(value = "tab_id", type = IdType.AUTO)
    private Long tabId;

    /** Tab 唯一标识(前端固定,只读) */
    private String tabKey;

    /** Tab 名称 */
    private String tabName;

    /** 普通态图标URL */
    private String iconNormal;

    /** 选中态图标URL */
    private String iconActive;

    /** 页面路径(前端固定,只读) */
    private String pagePath;

    /** 排序号(升序) */
    private Integer sort;

    /** 是否显示(1显示/0隐藏) */
    private Integer visible;

    /** 创建者 */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新者 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 备注 */
    private String remark;

    /** 逻辑删除标记(0存在/2删除) */
    @TableLogic
    private String delFlag;
}
