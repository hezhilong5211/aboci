package com.aurora.common.core.service;

import com.aurora.common.core.domain.entity.SysIcon;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 自定义图标业务层
 */
public interface ISysIconService extends IService<SysIcon> {

    /**
     * 上传图标
     * 
     * @param file     图标文件（PNG/SVG）
     * @param iconName 图标名称（可选，为空时自动生成）
     * @return 保存后的图标信息
     */
    SysIcon uploadIcon(MultipartFile file, String iconName);

    /**
     * 获取所有有效图标列表
     * 
     * @return 图标列表
     */
    List<SysIcon> listAllIcons();

    /**
     * 删除图标
     * 
     * @param iconId 图标ID
     * @return 是否成功
     */
    boolean deleteIcon(Long iconId);

    /**
     * 生成下一个自动图标名称
     * 
     * @return icon_001, icon_002 等格式
     */
    String generateNextIconName();
}
