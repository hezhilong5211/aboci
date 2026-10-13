package com.aurora.common.core.service.impl;

import com.aurora.common.core.domain.entity.SysIcon;
import com.aurora.common.core.mapper.SysIconMapper;
import com.aurora.common.core.service.ISysIconService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 自定义图标业务层实现
 */
@Service
public class SysIconServiceImpl extends ServiceImpl<SysIconMapper, SysIcon> implements ISysIconService {

    @Value("${spring.profile.upload-path}")
    private String uploadPath;

    /**
     * 上传图标
     */
    @Override
    public SysIcon uploadIcon(MultipartFile file, String iconName) {
        // 验证文件类型
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new RuntimeException("文件名不能为空");
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        if (!"png".equals(extension) && !"svg".equals(extension)) {
            throw new RuntimeException("仅支持 PNG 和 SVG 格式的图标");
        }

        // 验证文件大小（最大500KB）
        if (file.getSize() > 500 * 1024) {
            throw new RuntimeException("图标文件大小不能超过500KB");
        }

        // 生成图标名称
        String finalIconName = (iconName != null && !iconName.trim().isEmpty())
                ? iconName.trim()
                : generateNextIconName();

        // 检查名称是否已存在
        LambdaQueryWrapper<SysIcon> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysIcon::getIconName, finalIconName).eq(SysIcon::getDelFlag, "0");
        if (count(queryWrapper) > 0) {
            throw new RuntimeException("图标名称已存在：" + finalIconName);
        }

        // 生成存储文件名
        String storedFilename = "icon_" + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().substring(0, 8) + "." + extension;
        String iconDir = uploadPath + File.separator + "icons";
        String filePath = iconDir + File.separator + storedFilename;

        // 确保目录存在
        File dir = new File(iconDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 保存文件
        try {
            file.transferTo(new File(filePath));
        } catch (IOException e) {
            throw new RuntimeException("保存图标文件失败：" + e.getMessage());
        }

        // 保存数据库记录
        SysIcon sysIcon = new SysIcon();
        sysIcon.setIconName(finalIconName);
        sysIcon.setIconPath("/profile/icons/" + storedFilename); // 相对URL路径
        sysIcon.setIconType(extension);
        sysIcon.setCreateTime(LocalDateTime.now());
        sysIcon.setDelFlag("0");

        save(sysIcon);

        return sysIcon;
    }

    /**
     * 获取所有有效图标列表
     */
    @Override
    public List<SysIcon> listAllIcons() {
        LambdaQueryWrapper<SysIcon> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysIcon::getDelFlag, "0")
                .orderByDesc(SysIcon::getCreateTime);
        return list(queryWrapper);
    }

    /**
     * 删除图标
     */
    @Override
    public boolean deleteIcon(Long iconId) {
        SysIcon icon = getById(iconId);
        if (icon == null) {
            throw new RuntimeException("图标不存在");
        }

        // 删除文件
        String filePath = uploadPath + icon.getIconPath().replace("/profile", "");
        File file = new File(filePath);
        if (file.exists()) {
            file.delete();
        }

        // 逻辑删除数据库记录
        return removeById(iconId);
    }

    /**
     * 生成下一个自动图标名称
     */
    @Override
    public String generateNextIconName() {
        Integer nextSeq = baseMapper.getNextSequence();
        if (nextSeq == null) {
            nextSeq = 1;
        }
        return String.format("icon_%03d", nextSeq);
    }
}
